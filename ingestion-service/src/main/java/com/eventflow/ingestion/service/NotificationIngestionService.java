package com.eventflow.ingestion.service;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.dto.NotificationRequest;
import com.eventflow.ingestion.exception.RateLimitExceededException;
import com.eventflow.ingestion.exception.UnauthorizedException;
import com.eventflow.ingestion.model.ClientApp;
import com.eventflow.ingestion.repository.ClientAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationIngestionService {

  private final ClientAppRepository clientAppRepository;
  private final RateLimiterService rateLimiterService;
  private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

  public void processAndPublish(String apiKey, NotificationRequest request) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new UnauthorizedException("Missing API Key");
    }

    ClientApp clientApp = clientAppRepository.findByApiKey(apiKey)
      .orElseThrow(() -> new UnauthorizedException("Invalid API Key"));

    if (!rateLimiterService.isAllowed(clientApp.getClientId(), clientApp.getRateLimitPerMinute())) {
      throw new RateLimitExceededException("Rate limit exceeded for client: " + clientApp.getClientId());
    }

    String priority = (request.getPriority() != null && "HIGH".equalsIgnoreCase(request.getPriority())) ? "HIGH" : "LOW";

    NotificationEvent event = NotificationEvent.builder()
      .eventId(UUID.randomUUID().toString())
      .recipientId(request.getRecipientId())
      .userId(request.getRecipientId())
      .channel(request.getChannel())
      .priority(priority)
      .templateId(request.getTemplateId())
      .subject(request.getSubject())
      .body(request.getBody())
      .payload(request.getPayload())
      .createdAt(LocalDateTime.now())
      .build();

    String topic = determineTopic(request.getChannel(), priority);
    log.info("Routing notification event {} to topic {}", event.getEventId(), topic);
    kafkaTemplate.send(topic, event.getRecipientId(), event);
  }

  /**
   * Used by the "Relancer la notification" action (NotificationResendService)
   * to re-publish a notification for an existing notification_logs row,
   * reusing the exact same topic-routing rules as processAndPublish
   * (determineTopic) so a resent EMAIL/SMS/PUSH lands on the identical
   * channel topic and is picked up by the same consumer that handled it the
   * first time — which is what lets a resend produce a genuine new
   * notification_logs row instead of a client-side fake.
   *
   * Deliberately bypasses the API-key/rate-limit checks in
   * processAndPublish: this is an internal replay of a notification the
   * platform already accepted once, triggered only from
   * DashboardController — which is itself behind the JWT-protected
   * /api/dashboard/** filter chain (see SecurityConfig) — not a fresh
   * external submission that needs its own authorization.
   *
   * notification_logs doesn't retain the original subject/body/payload (see
   * NotificationLog's doc comment), so the resent event carries a generic
   * subject/body referencing the original event id rather than the exact
   * original content — the closest a resend can get without changing what
   * gets persisted on every delivery attempt.
   */
  public NotificationEvent publishResend(String recipientId, String channel, String originalEventId) {
    NotificationEvent event = NotificationEvent.builder()
      .eventId(UUID.randomUUID().toString())
      .recipientId(recipientId)
      .userId(recipientId)
      .channel(channel)
      .priority("LOW")
      .subject("Notification relancée")
      .body("Relance de la notification " + (originalEventId != null ? originalEventId : ""))
      .createdAt(LocalDateTime.now())
      .build();

    String topic = determineTopic(channel, "LOW");
    log.info("Resending notification event {} (replay of {}) to topic {}", event.getEventId(), originalEventId, topic);
    kafkaTemplate.send(topic, event.getRecipientId(), event);
    return event;
  }

  private String determineTopic(String channel, String priority) {
    String base = channel.toLowerCase();
    boolean isHigh = "HIGH".equalsIgnoreCase(priority);
    return switch (base) {
      case "email" -> isHigh ? KafkaTopicConfig.TOPIC_EMAIL_HIGH : KafkaTopicConfig.TOPIC_EMAIL_LOW;
      case "sms" -> isHigh ? KafkaTopicConfig.TOPIC_SMS_HIGH : KafkaTopicConfig.TOPIC_SMS_LOW;
      case "push" -> isHigh ? KafkaTopicConfig.TOPIC_PUSH_HIGH : KafkaTopicConfig.TOPIC_PUSH_LOW;
      default -> "notification." + base;
    };
  }
}
