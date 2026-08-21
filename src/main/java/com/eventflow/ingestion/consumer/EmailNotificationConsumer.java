package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.EmailService;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmailNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationConsumer.class);

    private final UserPreferenceRepository userPreferenceRepository;
    private final EmailService emailService;

    public EmailNotificationConsumer(UserPreferenceRepository userPreferenceRepository, EmailService emailService) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.emailService = emailService;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000),
            dltTopicSuffix = "-dlt"
    )
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

    @DltHandler
    public void handleDltNotification(NotificationEvent event,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        log.error("DEAD LETTER QUEUE (DLT): Received failed message in topic '{}'. Event payload: {}, Exception cause: {}",
                topic, event, exceptionMessage);
    }
}
