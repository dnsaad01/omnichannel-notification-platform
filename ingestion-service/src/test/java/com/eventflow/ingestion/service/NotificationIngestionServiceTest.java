package com.eventflow.ingestion.service;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.dto.NotificationRequest;
import com.eventflow.ingestion.exception.RateLimitExceededException;
import com.eventflow.ingestion.exception.UnauthorizedException;
import com.eventflow.ingestion.model.ClientApp;
import com.eventflow.ingestion.repository.ClientAppRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the actual authorization/rate-limit/topic-routing branching in
 * processAndPublish — this is the entry point for every notification the
 * platform ever sends via the direct API (as opposed to the Workflow
 * Engine), so it's worth pinning down precisely.
 */
@ExtendWith(MockitoExtension.class)
class NotificationIngestionServiceTest {

  @Mock
  private ClientAppRepository clientAppRepository;

  @Mock
  private RateLimiterService rateLimiterService;

  @Mock
  private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

  private NotificationIngestionService notificationIngestionService;

  @BeforeEach
  void setUp() {
    notificationIngestionService = new NotificationIngestionService(clientAppRepository, rateLimiterService, kafkaTemplate);
  }

  private ClientApp aClientApp() {
    return ClientApp.builder().id(1L).clientId("frontend-app").apiKey("my-secret-key-123").rateLimitPerMinute(100).build();
  }

  @Test
  void shouldRejectWhenApiKeyIsMissing() {
    NotificationRequest request = NotificationRequest.builder().recipientId("u1").channel("EMAIL").build();

    UnauthorizedException ex = assertThrows(UnauthorizedException.class,
      () -> notificationIngestionService.processAndPublish("  ", request));

    assertEquals("Missing API Key", ex.getMessage());
    verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
  }

  @Test
  void shouldRejectWhenApiKeyDoesNotMatchAnyClientApp() {
    when(clientAppRepository.findByApiKey("bad-key")).thenReturn(Optional.empty());
    NotificationRequest request = NotificationRequest.builder().recipientId("u1").channel("EMAIL").build();

    assertThrows(UnauthorizedException.class,
      () -> notificationIngestionService.processAndPublish("bad-key", request));

    verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
  }

  @Test
  void shouldRejectWhenTheClientHasExceededItsRateLimit() {
    ClientApp clientApp = aClientApp();
    when(clientAppRepository.findByApiKey("my-secret-key-123")).thenReturn(Optional.of(clientApp));
    when(rateLimiterService.isAllowed("frontend-app", 100)).thenReturn(false);
    NotificationRequest request = NotificationRequest.builder().recipientId("u1").channel("EMAIL").build();

    assertThrows(RateLimitExceededException.class,
      () -> notificationIngestionService.processAndPublish("my-secret-key-123", request));

    verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
  }

  @Test
  void shouldRouteAHighPriorityEmailToTheHighPriorityEmailTopic() {
    ClientApp clientApp = aClientApp();
    when(clientAppRepository.findByApiKey("my-secret-key-123")).thenReturn(Optional.of(clientApp));
    when(rateLimiterService.isAllowed("frontend-app", 100)).thenReturn(true);

    NotificationRequest request = NotificationRequest.builder()
      .recipientId("user@example.com")
      .channel("EMAIL")
      .priority("HIGH")
      .subject("Hi")
      .body("Body")
      .build();

    notificationIngestionService.processAndPublish("my-secret-key-123", request);

    ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_EMAIL_HIGH), eq("user@example.com"), eventCaptor.capture());

    NotificationEvent published = eventCaptor.getValue();
    assertEquals("user@example.com", published.getRecipientId());
    assertEquals("EMAIL", published.getChannel());
    assertEquals("HIGH", published.getPriority());
  }

  @Test
  void shouldDefaultToLowPriorityWhenPriorityIsAbsent() {
    ClientApp clientApp = aClientApp();
    when(clientAppRepository.findByApiKey("my-secret-key-123")).thenReturn(Optional.of(clientApp));
    when(rateLimiterService.isAllowed("frontend-app", 100)).thenReturn(true);

    NotificationRequest request = NotificationRequest.builder()
      .recipientId("+212600000000")
      .channel("SMS")
      .build();

    notificationIngestionService.processAndPublish("my-secret-key-123", request);

    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_SMS_LOW), eq("+212600000000"), any(NotificationEvent.class));
  }

  @Test
  void shouldRouteAnUnrecognizedChannelToAGenericNotificationTopicRatherThanFail() {
    ClientApp clientApp = aClientApp();
    when(clientAppRepository.findByApiKey("my-secret-key-123")).thenReturn(Optional.of(clientApp));
    when(rateLimiterService.isAllowed("frontend-app", 100)).thenReturn(true);

    NotificationRequest request = NotificationRequest.builder()
      .recipientId("device-token-xyz")
      .channel("WEBHOOK")
      .build();

    notificationIngestionService.processAndPublish("my-secret-key-123", request);

    verify(kafkaTemplate).send(eq("notification.webhook"), eq("device-token-xyz"), any(NotificationEvent.class));
  }
}
