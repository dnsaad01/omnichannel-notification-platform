package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.EmailService;
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

    private final EmailService emailService;
    private final UserPreferenceRepository preferenceRepository;

    @KafkaListener(topics = "notification.email", groupId = "notification-email-group")
    public void consume(NotificationEvent event) {
        log.info("Received notification event for user: {}", event.getUserId());

        Optional<UserPreference> preferenceOpt = preferenceRepository.findById(event.getUserId());

        if (preferenceOpt.isPresent()) {
            UserPreference pref = preferenceOpt.get();

            if (Boolean.FALSE.equals(pref.getEnabledEmail())) {
                log.info("Email notifications disabled for user: {}", event.getUserId());
                return;
            }

            if (pref.getQuietHoursStart() != null && pref.getQuietHoursEnd() != null) {
                LocalTime now = LocalTime.now();
                if (now.isAfter(pref.getQuietHoursStart()) && now.isBefore(pref.getQuietHoursEnd())) {
                    if (!"HIGH".equalsIgnoreCase(event.getPriority())) {
                        log.info("Suppressed email notification during quiet hours for user: {}", event.getUserId());
                        return;
                    }
                }
            }
        }

        String subject = event.getSubject() != null ? event.getSubject() : "Notification Alert";
        String body = event.getBody() != null ? event.getBody() : (event.getPayload() != null ? event.getPayload().toString() : "");

        try {
            emailService.sendEmail(event.getUserId(), subject, body);
            log.info("Successfully delivered email to user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to send email to user: {}", event.getUserId(), e);
        }
    }
}
