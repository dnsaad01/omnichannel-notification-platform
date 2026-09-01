package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
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

  private final UserPreferenceRepository preferenceRepository;
  private final TwilioSmsService smsService;

  @KafkaListener(topics = {KafkaTopicConfig.TOPIC_SMS_HIGH, KafkaTopicConfig.TOPIC_SMS_LOW}, groupId = "notification-sms-group")
  public void consume(NotificationEvent event) {
    log.info("Processing SMS notification for user: [{}] with priority [{}]", event.getRecipientId(), event.getPriority());

    Optional<UserPreference> preferenceOpt = preferenceRepository.findByUserId(event.getRecipientId());
    if (preferenceOpt.isPresent()) {
      UserPreference pref = preferenceOpt.get();
      if (!pref.isEnabledSms()) {
        log.info("SMS suppressed: SMS disabled in preferences for user [{}]", event.getRecipientId());
        return;
      }

      if (pref.getQuietHoursStart() != null && pref.getQuietHoursEnd() != null) {
        LocalTime now = LocalTime.now();
        if (now.isAfter(pref.getQuietHoursStart()) && now.isBefore(pref.getQuietHoursEnd())) {
          if (!"HIGH".equalsIgnoreCase(event.getPriority())) {
            log.info("SMS suppressed during quiet hours for user [{}]", event.getRecipientId());
            return;
          }
        }
      }
    }

    String messageBody = event.getBody() != null ? event.getBody() :
      (event.getPayload() != null ? event.getPayload().toString() : "Alert Notification");

    smsService.sendSms(event.getRecipientId(), messageBody);
  }
}
