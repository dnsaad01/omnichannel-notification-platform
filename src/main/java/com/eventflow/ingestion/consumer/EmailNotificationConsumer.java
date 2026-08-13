package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationConsumer {

    private final UserPreferenceRepository userPreferenceRepository;
    private final EmailService emailService;

    @KafkaListener(topics = "notification-email", groupId = "notification-worker-group")
    public void consumeEmailNotification(NotificationEvent event) {
        log.info("Received notification event from Kafka topic 'notification-email': {}", event);

        if (event == null || event.getUserId() == null) {
            log.warn("Received invalid or empty notification event");
            return;
        }

        Optional<UserPreference> userPrefOpt = userPreferenceRepository.findById(event.getUserId());

        if (userPrefOpt.isEmpty()) {
            log.warn("User preference not found for userId: {}", event.getUserId());
            return;
        }

        UserPreference userPreference = userPrefOpt.get();

        if (Boolean.TRUE.equals(userPreference.getEnabledEmail())) {
            String recipientEmail = userPreference.getEmailAddress();
            if (recipientEmail != null && !recipientEmail.isBlank()) {
                log.info("Email enabled for userId: {}. Dispatching email to {}", event.getUserId(), recipientEmail);
                emailService.sendEmail(recipientEmail, event.getSubject(), event.getBody());
            } else {
                log.warn("Email enabled for userId: {} but email_address is missing/blank", event.getUserId());
            }
        } else {
            log.info("Email notification disabled for userId: {}", event.getUserId());
        }
    }
}
