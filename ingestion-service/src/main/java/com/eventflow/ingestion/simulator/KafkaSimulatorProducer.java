package com.eventflow.ingestion.simulator;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.service.NotificationIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaSimulatorProducer {

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final NotificationIngestionService notificationIngestionService;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private int currentRatePerSecond = 2;

    private ScheduledExecutorService executorService;
    private final Random random = new Random();

    private static final List<String> CHANNELS = List.of("EMAIL", "SMS", "PUSH", "WHATSAPP");
    private static final List<String> PRIORITIES = List.of("HIGH", "LOW");
    private static final List<String> TEMPLATES = List.of(
            "TMPL-ORDER-CONFIRM",
            "TMPL-OTP-VERIFY",
            "TMPL-PAYMENT-SUCCESS",
            "TMPL-CART-ABANDONED",
            "TMPL-PROMO-FLASH"
    );
    private static final List<String> CLIENT_APPS = List.of(
            "E-Commerce-Gateway",
            "Mobile-Banking-App",
            "Auth-Identity-Service",
            "Logistics-Tracker"
    );
    private static final List<String> RECIPIENTS_EMAIL = List.of(
            "usr_john.doe@gmail.com",
            "usr_alice.smith@corporate.io",
            "usr_karim.benani@tech.ma",
            "usr_fatima.zahra@domain.org"
    );
    private static final List<String> RECIPIENTS_PHONE = List.of(
            "+212600112233",
            "+212661998877",
            "+33612345678",
            "+15550192834"
    );

    /**
     * Sends a single simulated notification event.
     */
    public NotificationEvent sendSingleSimulatedEvent(NotificationEvent customEvent) {
        NotificationEvent event = (customEvent != null) ? customEvent : generateRandomEvent();
        
        try {
            // 1. Publish to main ingestion topic (notification.ingestion)
            log.info("🚀 [Simulator] Publishing simulated event {} to {}", event.getEventId(), KafkaTopicConfig.TOPIC_INGESTION);
            kafkaTemplate.send(KafkaTopicConfig.TOPIC_INGESTION, event.getRecipientId(), event);

            // 2. Also route through ingestion logic to channel-specific Kafka topics
            String targetTopic = determineChannelTopic(event.getChannel(), event.getPriority());
            kafkaTemplate.send(targetTopic, event.getRecipientId(), event);

            totalSent.incrementAndGet();
            return event;
        } catch (Exception e) {
            log.error("❌ [Simulator] Error publishing event {}: {}", event.getEventId(), e.getMessage(), e);
            totalErrors.incrementAndGet();
            throw new RuntimeException("Failed to publish simulated event", e);
        }
    }

    /**
     * Sends a batch of N simulated events.
     */
    public List<NotificationEvent> sendBatchSimulatedEvents(int count) {
        int targetCount = Math.max(1, Math.min(count, 100)); // Cap batch between 1 and 100
        List<NotificationEvent> generatedEvents = new ArrayList<>();
        
        for (int i = 0; i < targetCount; i++) {
            generatedEvents.add(sendSingleSimulatedEvent(null));
        }
        return generatedEvents;
    }

    /**
     * Starts continuous scheduled event generation.
     */
    public synchronized boolean startSimulation(int ratePerSecond) {
        if (isRunning.get()) {
            log.warn("⚠️ [Simulator] Simulation is already running.");
            return false;
        }

        this.currentRatePerSecond = Math.max(1, Math.min(ratePerSecond, 20));
        this.isRunning.set(true);

        this.executorService = Executors.newSingleThreadScheduledExecutor();
        long periodMs = 1000 / currentRatePerSecond;

        this.executorService.scheduleAtFixedRate(() -> {
            if (isRunning.get()) {
                try {
                    sendSingleSimulatedEvent(null);
                } catch (Exception e) {
                    log.error("⚠️ [Simulator] Scheduled dispatch failed: {}", e.getMessage());
                }
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);

        log.info("🟢 [Simulator] Background simulation STARTED at rate of {} msg/sec", currentRatePerSecond);
        return true;
    }

    /**
     * Stops continuous simulation.
     */
    public synchronized boolean stopSimulation() {
        if (!isRunning.get()) {
            return false;
        }

        isRunning.set(false);
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        log.info("🔴 [Simulator] Background simulation STOPPED. Total sent: {}", totalSent.get());
        return true;
    }

    /**
     * Returns the current simulation status and metrics.
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "active", isRunning.get(),
                "ratePerSecond", currentRatePerSecond,
                "totalSent", totalSent.get(),
                "totalErrors", totalErrors.get(),
                "targetTopic", KafkaTopicConfig.TOPIC_INGESTION,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    /**
     * Generates a rich random notification event simulation payload.
     */
    public NotificationEvent generateRandomEvent() {
        String channel = CHANNELS.get(random.nextInt(CHANNELS.size()));
        String priority = PRIORITIES.get(random.nextInt(PRIORITIES.size()));
        String templateId = TEMPLATES.get(random.nextInt(TEMPLATES.size()));
        String clientApp = CLIENT_APPS.get(random.nextInt(CLIENT_APPS.size()));
        String recipient = "SMS".equals(channel) || "WHATSAPP".equals(channel)
                ? RECIPIENTS_PHONE.get(random.nextInt(RECIPIENTS_PHONE.size()))
                : RECIPIENTS_EMAIL.get(random.nextInt(RECIPIENTS_EMAIL.size()));

        Map<String, Object> metadataPayload = new HashMap<>();
        metadataPayload.put("clientApp", clientApp);
        metadataPayload.put("environment", "SIMULATION");
        metadataPayload.put("ipAddress", "192.168.1." + (10 + random.nextInt(200)));
        metadataPayload.put("deviceOS", random.nextBoolean() ? "iOS 18.1" : "Android 15");
        metadataPayload.put("simulatedAt", System.currentTimeMillis());
        metadataPayload.put("orderTotal", String.format("%.2f USD", 15.0 + random.nextDouble() * 250.0));

        return NotificationEvent.builder()
                .eventId("SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .recipientId(recipient)
                .userId(recipient)
                .channel(channel)
                .priority(priority)
                .templateId(templateId)
                .subject("Simulated " + channel + " Notification [" + templateId + "]")
                .body("Hello, this is an automated simulated payload generated for client application: " + clientApp)
                .payload(metadataPayload)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String determineChannelTopic(String channel, String priority) {
        String base = channel.toLowerCase();
        boolean isHigh = "HIGH".equalsIgnoreCase(priority);
        return switch (base) {
            case "email" -> isHigh ? KafkaTopicConfig.TOPIC_EMAIL_HIGH : KafkaTopicConfig.TOPIC_EMAIL_LOW;
            case "sms" -> isHigh ? KafkaTopicConfig.TOPIC_SMS_HIGH : KafkaTopicConfig.TOPIC_SMS_LOW;
            case "push" -> isHigh ? KafkaTopicConfig.TOPIC_PUSH_HIGH : KafkaTopicConfig.TOPIC_PUSH_LOW;
            default -> "notification." + base;
        };
    }

    @PreDestroy
    public void cleanup() {
        stopSimulation();
    }
}
