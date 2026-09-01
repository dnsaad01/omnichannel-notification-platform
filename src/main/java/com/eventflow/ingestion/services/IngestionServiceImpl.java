package com.eventflow.ingestion.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionServiceImpl implements IngestionService {

    private final PreferenceCheckerService preferenceCheckerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public boolean processNotification(String recipientId, String channel, Object payload) {
        log.info("Checking preferences for recipient: {} on channel: {}", recipientId, channel);

        // 1. فحص إعدادات المستخدم والـ Quiet Hours
        boolean isAllowed = preferenceCheckerService.canSendNotification(recipientId, channel);

        if (!isAllowed) {
            log.warn("Notification skipped for recipient {} on channel {}", recipientId, channel);
            return false;
        }

        // 2. إرسال إلى Kafka Topic المناسب
        String topic = "notification." + channel.toLowerCase();
        log.info("Pushing notification event to Kafka topic: {}", topic);
        kafkaTemplate.send(topic, recipientId, payload);

        return true;
    }
}