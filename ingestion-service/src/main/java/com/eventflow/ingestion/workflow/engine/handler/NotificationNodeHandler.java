package com.eventflow.ingestion.workflow.engine.handler;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.EmailService;
import com.eventflow.ingestion.service.NotificationLogService;
import com.eventflow.ingestion.workflow.engine.ContextUtils;
import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.model.NotificationTemplate;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import com.eventflow.ingestion.workflow.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes a NOTIFICATION node: resolves the referenced template, renders
 * {{placeholders}} against the execution context, then dispatches.
 *
 * SMS/PUSH publish straight onto the existing per-channel topic
 * (TOPIC_SMS_HIGH, etc.) via KafkaTemplate — the same topics
 * SmsNotificationConsumer/PushNotificationConsumer already listen on,
 * unchanged, fire-and-forget. The engine bypasses the public
 * /api/v1/notifications/send + API-key/rate-limit path deliberately (plan
 * §4): there is no external caller here to authenticate or throttle.
 *
 * EMAIL is different (Phase 4 bugfix — see dispatchEmailSynchronously's doc
 * comment): it calls EmailService.sendEmail(...) directly and
 * synchronously, on this same thread, instead of going through Kafka/
 * EmailNotificationConsumer at all — so a real SMTP failure fails the node
 * (and the execution) immediately instead of being swallowed three classes
 * away with the execution already having moved on to COMPLETED.
 *
 * Expected node config:
 * {
 *   "templateId": 5,              // numeric NotificationTemplate id, OR
 *   "templateId": "Relance panier",// its name — either resolves
 *   "channel": "EMAIL",           // optional; defaults to the template's own channel
 *   "recipientPath": "user.email" // optional dot-path into context; defaults
 *                                  // try recipientId, then userId, then email, then phone
 * }
 *
 * Never a suspend point — always CONTINUE, per the architecture plan.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationNodeHandler implements NodeHandler {

  private static final String[] DEFAULT_RECIPIENT_KEYS = {"recipientId", "userId", "email", "phone"};

  private final NotificationTemplateRepository notificationTemplateRepository;
  private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

  /** Phase 4 fix: EMAIL is dispatched synchronously through these two —
   *  see dispatchEmailSynchronously's doc comment for the full story on
   *  why, and appendTrackingPixel below is unchanged. */
  private final EmailService emailService;
  private final UserPreferenceRepository userPreferenceRepository;
  private final NotificationLogService notificationLogService;

  /** Phase 4: base URL the email-open tracking pixel is served from (see
   *  TrackingController). Not final / not constructor-injected — @Value
   *  fields sit alongside @RequiredArgsConstructor's final fields the same
   *  way EmailService already does it elsewhere in this codebase. */
  @Value("${app.tracking.base-url:http://localhost:8082}")
  private String trackingBaseUrl;

  @Override
  public String nodeType() {
    return "NOTIFICATION";
  }

  @Override
  public NodeOutcome handle(WorkflowExecution execution, NodeDef node, Map<String, Object> context) {
    Map<String, Object> config = node.getConfig();
    Object templateRef = config != null ? config.get("templateId") : null;
    if (templateRef == null) {
      return NodeOutcome.fail("NOTIFICATION node [" + node.getId() + "] has no templateId configured");
    }

    NotificationTemplate template = resolveTemplate(templateRef)
      .orElseThrow(() -> new IllegalStateException("No template found for templateId=" + templateRef));

    String channel = config.get("channel") != null ? String.valueOf(config.get("channel")).toUpperCase() : template.getChannel();
    String recipient = resolveRecipient(config, context);
    if (recipient == null || recipient.isBlank()) {
      return NodeOutcome.fail("NOTIFICATION node [" + node.getId() + "] could not resolve a recipient from the execution context");
    }

    String subject = ContextUtils.interpolate(template.getSubject(), context);
    String body = ContextUtils.interpolate(template.getBody(), context);

    // Phase 4 fix (see dispatchEmailSynchronously's doc comment): EMAIL no
    // longer goes through the fire-and-forget Kafka publish below at all —
    // it's the only channel where a silent async failure was reported as a
    // real bug, and the fix is to send it on this thread and actually
    // observe the result before deciding CONTINUE vs FAIL.
    if ("EMAIL".equalsIgnoreCase(channel)) {
      return dispatchEmailSynchronously(execution, node, context, recipient, subject, body);
    }

    NotificationEvent event = NotificationEvent.builder()
      .eventId(UUID.randomUUID().toString())
      .recipientId(recipient)
      .userId(recipient)
      .channel(channel)
      .priority("HIGH")
      .templateId(String.valueOf(template.getId()))
      .subject(subject)
      .body(body)
      .createdAt(LocalDateTime.now())
      .build();

    String topic = resolveChannelTopic(channel);
    log.info("[Workflow {}] NOTIFICATION node [{}] dispatching {} to {} via {}", execution.getId(), node.getId(), channel, recipient, topic);
    kafkaTemplate.send(topic, recipient, event);

    ContextUtils.set(context, channel.toLowerCase() + ".sent", true);

    return NodeOutcome.continueTo(channelLabel(channel) + " envoyé à " + recipient);
  }

  /**
   * ⚠️ BEHAVIOR CHANGE (Phase 4 bugfix) — READ BEFORE TOUCHING
   *
   * The bug: EMAIL used to go through the exact same fire-and-forget path
   * as SMS/PUSH below — publish a NotificationEvent onto TOPIC_EMAIL_HIGH
   * and immediately return CONTINUE (then the engine happily proceeds to
   * COMPLETED), regardless of whether EmailNotificationConsumer — a
   * different class, on a different Kafka consumer thread, at some later
   * and completely decoupled moment — ever actually got the email out.
   * That consumer already calls EmailService.sendEmail(...) inside a
   * try/catch and correctly logs + records STATUS_FAILED on a real SMTP/
   * auth error (see EmailNotificationConsumer#consume) — EmailService
   * itself was never silently swallowing anything, it already logs and
   * rethrows on every failure (see its own class doc comment). The actual
   * gap was architectural: nothing ever wired that consumer-side failure
   * back to the WorkflowExecution that triggered it, because the workflow
   * had already marked itself CONTINUE the instant the Kafka *publish*
   * (not the send) succeeded. That's exactly why EXEC-4 showed COMPLETED
   * with "dispatching EMAIL to ..." in the logs — that log line is the
   * producer confirming the message was queued, not that Gmail accepted it.
   *
   * The fix: for EMAIL specifically, call EmailService.sendEmail(...)
   * directly and synchronously, right here, instead of publishing to Kafka
   * at all. Any thrown exception (bad app password, connection refused,
   * STARTTLS failure — EmailService already catches, logs and rethrows all
   * of these as a RuntimeException with the real cause attached) is caught
   * below and turned into NodeOutcome.fail(...), which
   * WorkflowExecutionEngine#fail turns into ExecutionStatus.FAILED with the
   * actual SMTP error text recorded on the execution's own timeline — not
   * a silent CONTINUE/COMPLETED three classes and a Kafka hop away.
   *
   * Trade-off, disclosed per this project's standing rule about changing
   * established behavior: WorkflowExecutionEngine#advance now blocks on a
   * live SMTP round trip for every EMAIL node (normally well under a
   * second). A genuinely hung/unreachable SMTP host would stall that
   * engine call until JavaMailSender's own timeouts elapse — see the new
   * mail.smtp.{connectiontimeout,timeout,writetimeout} entries added to
   * application.properties alongside this change specifically so "hung"
   * degrades to "fails after 5s" rather than "hangs forever" now that this
   * runs inline. SMS/PUSH are deliberately left exactly as they were
   * (async, via Kafka, below) — they weren't reported broken and widening
   * this fix to them would touch two more consumers for no requested benefit.
   *
   * Preference suppression (recipient opted out of email, or quiet hours)
   * is preserved here by duplicating EmailNotificationConsumer's exact
   * check — same "deliberate duplicate over shared refactor" convention
   * this class already uses in resolveChannelTopic, so nothing about the
   * existing async consumer path is touched by this fix either.
   */
  private NodeOutcome dispatchEmailSynchronously(WorkflowExecution execution, NodeDef node, Map<String, Object> context,
                                                  String recipient, String subject, String body) {
    String eventId = UUID.randomUUID().toString();
    String htmlBody = appendTrackingPixel(body, execution.getId());

    Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUserId(recipient);
    if (preferenceOpt.isPresent() && !preferenceOpt.get().isEnabledEmail()) {
      log.info("[Workflow {}] NOTIFICATION node [{}] EMAIL suppressed: recipient {} has email disabled in preferences",
        execution.getId(), node.getId(), recipient);
      notificationLogService.record(eventId, recipient, "EMAIL", NotificationLogService.STATUS_SUPPRESSED);
      return NodeOutcome.continueTo("Email à " + recipient + " ignoré (désactivé dans les préférences utilisateur)");
    }
    // Quiet hours only ever suppresses non-HIGH priority, and every
    // workflow-originated notification is HIGH (see the SMS/PUSH branch
    // below) — so this never actually fires today. Kept anyway for exact
    // parity with EmailNotificationConsumer, and as a no-cost safety net
    // if that hardcoded priority ever changes.
    if (preferenceOpt.isPresent()) {
      UserPreference pref = preferenceOpt.get();
      if (pref.getQuietHoursStart() != null && pref.getQuietHoursEnd() != null) {
        LocalTime now = LocalTime.now();
        if (now.isAfter(pref.getQuietHoursStart()) && now.isBefore(pref.getQuietHoursEnd())) {
          log.info("[Workflow {}] NOTIFICATION node [{}] EMAIL suppressed: quiet hours in effect for recipient {}",
            execution.getId(), node.getId(), recipient);
          notificationLogService.record(eventId, recipient, "EMAIL", NotificationLogService.STATUS_SUPPRESSED);
          return NodeOutcome.continueTo("Email à " + recipient + " ignoré (heures calmes)");
        }
      }
    }

    log.info("[Workflow {}] NOTIFICATION node [{}] sending EMAIL to {} synchronously", execution.getId(), node.getId(), recipient);
    try {
      emailService.sendEmail(recipient, subject, htmlBody);
    } catch (Exception e) {
      log.error("[Workflow {}] NOTIFICATION node [{}] EMAIL to {} failed: {}",
        execution.getId(), node.getId(), recipient, e.getMessage(), e);
      notificationLogService.record(eventId, recipient, "EMAIL", NotificationLogService.STATUS_FAILED);
      return NodeOutcome.fail("Échec de l'envoi de l'email à " + recipient + " : " + e.getMessage());
    }

    log.info("[Workflow {}] NOTIFICATION node [{}] EMAIL to {} delivered", execution.getId(), node.getId(), recipient);
    notificationLogService.record(eventId, recipient, "EMAIL", NotificationLogService.STATUS_DELIVERED);
    ContextUtils.set(context, "email.sent", true);
    return NodeOutcome.continueTo("Email envoyé à " + recipient);
  }

  private java.util.Optional<NotificationTemplate> resolveTemplate(Object templateRef) {
    if (templateRef instanceof Number number) {
      return notificationTemplateRepository.findById(number.longValue());
    }
    String asString = String.valueOf(templateRef);
    try {
      return notificationTemplateRepository.findById(Long.parseLong(asString));
    } catch (NumberFormatException notNumeric) {
      return notificationTemplateRepository.findByNameIgnoreCase(asString);
    }
  }

  private String resolveRecipient(Map<String, Object> config, Map<String, Object> context) {
    Object recipientPath = config.get("recipientPath");
    if (recipientPath != null) {
      Object resolved = ContextUtils.resolve(context, String.valueOf(recipientPath));
      if (resolved != null) {
        return String.valueOf(resolved);
      }
    }
    for (String key : DEFAULT_RECIPIENT_KEYS) {
      Object value = context.get(key);
      if (value != null) {
        return String.valueOf(value);
      }
    }
    return null;
  }

  /** Mirrors NotificationIngestionService#determineTopic — kept as a small,
   *  deliberate duplicate rather than a shared refactor, so nothing about
   *  the existing public ingestion path is touched by the Workflow Engine. */
  private String resolveChannelTopic(String channel) {
    return switch (channel.toLowerCase()) {
      case "email" -> KafkaTopicConfig.TOPIC_EMAIL_HIGH;
      case "sms" -> KafkaTopicConfig.TOPIC_SMS_HIGH;
      case "push" -> KafkaTopicConfig.TOPIC_PUSH_HIGH;
      default -> throw new IllegalStateException("Unsupported notification channel: " + channel);
    };
  }

  private String channelLabel(String channel) {
    return switch (channel.toLowerCase()) {
      case "email" -> "Email";
      case "sms" -> "SMS";
      case "push" -> "Push";
      default -> channel;
    };
  }

  /** Appends the Phase 4 tracking pixel (TrackingController,
   *  GET /api/tracking/open/{executionId}) to an EMAIL body. A 1x1
   *  invisible image whose fetch is how EmailTrackingService learns the
   *  recipient opened the message — see both classes' doc comments for the
   *  full round trip, including the downstream GATEWAY node that reads
   *  context.email.opened. */
  private String appendTrackingPixel(String body, Long executionId) {
    String pixelUrl = trackingBaseUrl + "/api/tracking/open/" + executionId;
    String pixelTag = "<img src=\"" + pixelUrl + "\" width=\"1\" height=\"1\" style=\"display:none\" alt=\"\" />";
    return (body != null ? body : "") + pixelTag;
  }
}
