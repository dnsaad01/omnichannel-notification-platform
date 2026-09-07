package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.FcmPushService;
import com.eventflow.ingestion.service.NotificationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationConsumer {

  private static final String CHANNEL = "PUSH";

  private final UserPreferenceRepository preferenceRepository;
  private final FcmPushService pushService;
  private final NotificationLogService notificationLogService;

  @KafkaListener(topics = {KafkaTopicConfig.TOPIC_PUSH_HIGH, KafkaTopicConfig.TOPIC_PUSH_LOW}, groupId = "notification-push-group")
  public void consume(NotificationEvent event) {
    log.info("Processing PUSH event [{}] for recipient [{}]", event.getEventId(), event.getRecipientId());

    Optional<UserPreference> preferenceOpt = preferenceRepository.findByUserId(event.getRecipientId());
    if (preferenceOpt.isPresent()) {
      UserPreference pref = preferenceOpt.get();
      if (!pref.isEnabledPush()) {
        log.info("Push notification disabled for user [{}]", event.getRecipientId());
        notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_SUPPRESSED);
        return;
      }

      if (pref.getQuietHoursStart() != null && pref.getQuietHoursEnd() != null) {
        LocalTime now = LocalTime.now();
        if (now.isAfter(pref.getQuietHoursStart()) && now.isBefore(pref.getQuietHoursEnd())) {
          if (!"HIGH".equalsIgnoreCase(event.getPriority())) {
            log.info("Push notification suppressed during quiet hours for [{}]", event.getRecipientId());
            notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_SUPPRESSED);
            return;
          }
        }
      }
    }

    String title = event.getSubject() != null ? event.getSubject() : "Notification Alert";
    String body = event.getBody() != null ? event.getBody() : "You have a new alert notification";
    Map<String, String> data = new HashMap<>();
    if (event.getPayload() != null) {
      event.getPayload().forEach((k, v) -> data.put(k, String.valueOf(v)));
    }

    try {
      pushService.sendPushNotification(event.getRecipientId(), title, body, data);
      notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_DELIVERED);
    } catch (Exception e) {
      log.error("Failed to deliver push notification to: {}", event.getRecipientId(), e);
      notificationLogService.record(event.getEventId(), event.getRecipientId(), CHANNEL, NotificationLogService.STATUS_FAILED);
    }
  }
}
