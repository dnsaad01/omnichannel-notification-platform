package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.NotificationLogService;
import com.eventflow.ingestion.service.TwilioSmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsNotificationConsumer {

  private static final String CHANNEL = "SMS";

  private final UserPreferenceRepository preferenceRepository;
  private final TwilioSmsService smsService;
  private final NotificationLogService notificationLogService;

  @KafkaListener(topics = {KafkaTopicConfig.TOPIC_SMS_HIGH, KafkaTopicConfig.TOPIC_SMS_LOW}, groupId = "notification-sms-group")
  public void consume(NotificationEvent event) {
    log.info("Processing SMS notification for user: [{}] with priority [{}]", event.getRecipientId(), event.getPriority());

    Optional<UserPreference> preferenceOpt = preferenceRepository.findByUserId(event.getRecipientId());
    if (preferenceOpt.isPresent()) {
      UserPreference pref = preferenceOpt.get();
      if (!pref.isEnabledSms()) {
        log.info("SMS suppressed: SMS disabled in preferences for user [{}]", event.getRecipientId());
        notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_SUPPRESSED);
        return;
      }

      if (pref.getQuietHoursStart() != null && pref.getQuietHoursEnd() != null) {
        LocalTime now = LocalTime.now();
        if (now.isAfter(pref.getQuietHoursStart()) && now.isBefore(pref.getQuietHoursEnd())) {
          if (!"HIGH".equalsIgnoreCase(event.getPriority())) {
            log.info("SMS suppressed during quiet hours for user [{}]", event.getRecipientId());
            notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_SUPPRESSED);
            return;
          }
        }
      }
    }

    String messageBody = event.getBody() != null ? event.getBody() :
      (event.getPayload() != null ? event.getPayload().toString() : "Alert Notification");

    try {
      smsService.sendSms(event.getRecipientId(), messageBody);
      notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_DELIVERED);
    } catch (Exception e) {
      log.error("Failed to deliver SMS to: {}", event.getRecipientId(), e);
      notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_FAILED);
    }
  }
}
