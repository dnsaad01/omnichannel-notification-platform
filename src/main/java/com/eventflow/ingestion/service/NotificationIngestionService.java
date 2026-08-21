package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.dto.NotificationRequest;
import com.eventflow.ingestion.exception.RateLimitExceededException;
import com.eventflow.ingestion.exception.UnauthorizedException;
import com.eventflow.ingestion.model.ClientApp;
import com.eventflow.ingestion.repository.ClientAppRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(NotificationIngestionService.class);
    private static final String EMAIL_TOPIC = "notification-email";

    private final ClientAppRepository clientAppRepository;
    private final RateLimiterService rateLimiterService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public NotificationIngestionService(ClientAppRepository clientAppRepository, RateLimiterService rateLimiterService, KafkaTemplate<String, NotificationEvent> kafkaTemplate) {
        this.clientAppRepository = clientAppRepository;
        this.rateLimiterService = rateLimiterService;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void processNotification(String apiKey, NotificationRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UnauthorizedException("Missing X-API-KEY header");
        }

        ClientApp clientApp = clientAppRepository.findByApiKeyHash(apiKey)
                .orElseThrow(() -> new UnauthorizedException("Invalid API Key"));

        boolean allowed = rateLimiterService.isAllowed(clientApp.getClientId(), clientApp.getRateLimitQuota());
        if (!allowed) {
            throw new RateLimitExceededException("Rate limit quota exceeded for client: " + clientApp.getClientId());
        }

        NotificationEvent event = NotificationEvent.builder()
                .eventId("evt_" + UUID.randomUUID().toString().replace("-", ""))
                .userId(request.getUserId())
                .channel(request.getChannel())
                .subject(request.getSubject())
                .body(request.getBody())
                .timestamp(Instant.now().toString())
                .build();

        log.info("Publishing NotificationEvent to Kafka topic '{}' for userId: '{}'", EMAIL_TOPIC, event.getUserId());
        kafkaTemplate.send(EMAIL_TOPIC, event.getUserId(), event);
    }
}
