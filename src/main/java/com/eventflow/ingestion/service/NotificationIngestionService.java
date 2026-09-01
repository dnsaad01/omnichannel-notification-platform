package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.dto.NotificationRequest;
import com.eventflow.ingestion.exception.RateLimitExceededException;
import com.eventflow.ingestion.exception.UnauthorizedException;
import com.eventflow.ingestion.model.ClientApp;
import com.eventflow.ingestion.repository.ClientAppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationIngestionService {

    private final ClientAppRepository clientAppRepository;
    private final RateLimiterService rateLimiterService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public void processAndPublish(String apiKey, NotificationRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UnauthorizedException("Missing API Key");
        }

        ClientApp clientApp = clientAppRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new UnauthorizedException("Invalid API Key"));

        if (!rateLimiterService.isAllowed(clientApp.getClientId(), clientApp.getRateLimitPerMinute())) {
            throw new RateLimitExceededException("Rate limit exceeded for client: " + clientApp.getClientId());
        }

        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .recipientId(request.getRecipientId())
                .channel(request.getChannel())
                .priority(request.getPriority())
                .templateId(request.getTemplateId())
                .payload(request.getPayload())
                .createdAt(LocalDateTime.now())
                .build();

        String topic = "notification." + request.getChannel().toLowerCase();
        kafkaTemplate.send(topic, event.getRecipientId(), event);
    }
}
