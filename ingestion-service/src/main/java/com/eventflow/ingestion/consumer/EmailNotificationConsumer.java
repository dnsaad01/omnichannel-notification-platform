package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.EmailService;
import com.eventflow.ingestion.service.NotificationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationConsumer {

  private static final String CHANNEL = "EMAIL";

  private final UserPreferenceRepository preferenceRepository;
  private final EmailService emailService;
  private final NotificationLogService notificationLogService;

  @KafkaListener(topics = {KafkaTopicConfig.TOPIC_EMAIL_HIGH, KafkaTopicConfig.TOPIC_EMAIL_LOW}, groupId = "notification-email-group")
  public void consume(NotificationEvent event) {
    log.info("Processing EMAIL notification for user: [{}] with priority [{}]", event.getRecipientId(), event.getPriority());

    Optional<UserPreference> preferenceOpt = preferenceRepository.findByUserId(event.getRecipientId());
    if (preferenceOpt.isPresent()) {
      UserPreference pref = preferenceOpt.get();
      if (!pref.isEnabledEmail()) {
        log.info("Email suppressed: Email disabled in preferences for user [{}]", event.getRecipientId());
        notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_SUPPRESSED);
        return;
      }

      if (pref.getQuietHoursStart() != null && pref.getQuietHoursEnd() != null) {
        LocalTime now = LocalTime.now();
        if (now.isAfter(pref.getQuietHoursStart()) && now.isBefore(pref.getQuietHoursEnd())) {
          if (!"HIGH".equalsIgnoreCase(event.getPriority())) {
            log.info("Email suppressed during quiet hours for user [{}]", event.getRecipientId());
            notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_SUPPRESSED);
            return;
          }
        }
      }
    }

    String subject = event.getSubject() != null ? event.getSubject() : "Notification Alert";
    String body = event.getBody() != null ? event.getBody() :
      (event.getPayload() != null ? event.getPayload().toString() : "No content provided");

    try {
      emailService.sendEmail(event.getRecipientId(), subject, body);
      log.info("Successfully delivered email to user: {}", event.getRecipientId());
      notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_DELIVERED);
    } catch (Exception e) {
      log.error("Failed to deliver email to user: {}", event.getRecipientId(), e);
      notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_FAILED);
    }
  }
}
