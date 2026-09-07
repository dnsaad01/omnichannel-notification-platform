package com.eventflow.ingestion.service;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.dto.NotificationRequest;
import com.eventflow.ingestion.exception.RateLimitExceededException;
import com.eventflow.ingestion.exception.UnauthorizedException;
import com.eventflow.ingestion.model.ClientApp;
import com.eventflow.ingestion.repository.ClientAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
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

    String priority = (request.getPriority() != null && "HIGH".equalsIgnoreCase(request.getPriority())) ? "HIGH" : "LOW";

    NotificationEvent event = NotificationEvent.builder()
      .eventId(UUID.randomUUID().toString())
      .recipientId(request.getRecipientId())
      .userId(request.getRecipientId())
      .channel(request.getChannel())
      .priority(priority)
      .templateId(request.getTemplateId())
      .payload(request.getPayload())
      .createdAt(LocalDateTime.now())
      .build();

    String topic = determineTopic(request.getChannel(), priority);
    log.info("Routing notification event {} to topic {}", event.getEventId(), topic);
    kafkaTemplate.send(topic, event.getRecipientId(), event);
  }

  private String determineTopic(String channel, String priority) {
    String base = channel.toLowerCase();
    boolean isHigh = "HIGH".equalsIgnoreCase(priority);
    return switch (base) {
      case "email" -> isHigh ? KafkaTopicConfig.TOPIC_EMAIL_HIGH : KafkaTopicConfig.TOPIC_EMAIL_LOW;
      case "sms" -> isHigh ? KafkaTopicConfig.TOPIC_SMS_HIGH : KafkaTopicConfig.TOPIC_SMS_LOW;
      case "push" -> isHigh ? KafkaTopicConfig.TOPIC_PUSH_HIGH : KafkaTopicConfig.TOPIC_PUSH_LOW;
      default -> "notification." + base;
    };
  }
}
