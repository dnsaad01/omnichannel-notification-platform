package com.eventflow.ingestion.workflow.engine.handler;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.EmailService;
import com.eventflow.ingestion.service.NotificationLogService;
import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.model.NotificationTemplate;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import com.eventflow.ingestion.workflow.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationNodeHandlerTest {

  @Mock
  private NotificationTemplateRepository notificationTemplateRepository;

  @Mock
  private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

  @Mock
  private EmailService emailService;

  @Mock
  private UserPreferenceRepository userPreferenceRepository;

  @Mock
  private NotificationLogService notificationLogService;

  private NotificationNodeHandler handler;

  private final WorkflowExecution execution = WorkflowExecution.builder().id(42L).build();

  @BeforeEach
  void setUp() {
    handler = new NotificationNodeHandler(notificationTemplateRepository, kafkaTemplate, emailService,
      userPreferenceRepository, notificationLogService);
    ReflectionTestUtils.setField(handler, "trackingBaseUrl", "http://localhost:8082");
  }

  private NotificationTemplate template(Long id, String channel) {
    return NotificationTemplate.builder()
      .id(id).name("Welcome").channel(channel)
      .subject("Hi {{user.firstName}}").body("Hello {{user.firstName}}")
      .build();
  }

  private NodeDef notificationNode(Map<String, Object> config) {
    return NodeDef.builder().id("n1").type("NOTIFICATION").config(config).build();
  }

  @Test
  void nodeTypeShouldBeNotification() {
    assertEquals("NOTIFICATION", handler.nodeType());
  }

  @Test
  void handleShouldFailWhenNoTemplateIdIsConfigured() {
    NodeDef node = notificationNode(Map.of());

    NodeOutcome outcome = handler.handle(execution, node, Map.of());

    assertEquals(NodeOutcome.Type.FAIL, outcome.getType());
    assertEquals("NOTIFICATION node [n1] has no templateId configured", outcome.getErrorMessage());
    verify(notificationTemplateRepository, never()).findById(any());
  }

  @Test
  void handleShouldThrowWhenTheReferencedTemplateDoesNotExist() {
    NodeDef node = notificationNode(Map.of("templateId", 5));
    when(notificationTemplateRepository.findById(5L)).thenReturn(Optional.empty());

    IllegalStateException ex = assertThrows(IllegalStateException.class,
      () -> handler.handle(execution, node, Map.of()));
    assertTrue(ex.getMessage().contains("templateId=5"));
  }

  @Test
  void handleShouldResolveATemplateReferencedByNameWhenTheIdIsNotNumeric() {
    NodeDef node = notificationNode(Map.of("templateId", "Welcome", "channel", "sms"));
    when(notificationTemplateRepository.findByNameIgnoreCase("Welcome")).thenReturn(Optional.of(template(9L, "SMS")));
    Map<String, Object> context = new HashMap<>(Map.of("userId", "u1"));

    NodeOutcome outcome = handler.handle(execution, node, context);

    assertEquals(NodeOutcome.Type.CONTINUE, outcome.getType());
    verify(notificationTemplateRepository).findByNameIgnoreCase("Welcome");
    verify(notificationTemplateRepository, never()).findById(any());
  }

  @Test
  void handleShouldFailWhenNoRecipientCanBeResolvedFromTheContext() {
    NodeDef node = notificationNode(Map.of("templateId", 5));
    when(notificationTemplateRepository.findById(5L)).thenReturn(Optional.of(template(5L, "SMS")));

    NodeOutcome outcome = handler.handle(execution, node, Map.of());

    assertEquals(NodeOutcome.Type.FAIL, outcome.getType());
    assertEquals("NOTIFICATION node [n1] could not resolve a recipient from the execution context", outcome.getErrorMessage());
  }

  @Test
  void handleShouldDispatchSmsToTheHighPriorityTopicAndMarkTheContextSent() {
    NodeDef node = notificationNode(Map.of("templateId", 5));
    when(notificationTemplateRepository.findById(5L)).thenReturn(Optional.of(template(5L, "SMS")));
    Map<String, Object> context = new HashMap<>();
    context.put("recipientId", "+15550001111");
    context.put("user", Map.of("firstName", "Alex"));

    NodeOutcome outcome = handler.handle(execution, node, context);

    assertEquals(NodeOutcome.Type.CONTINUE, outcome.getType());
    assertEquals(Boolean.TRUE, ((Map<?, ?>) context.get("sms")).get("sent"));

    ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_SMS_HIGH), eq("+15550001111"), captor.capture());
    assertEquals("Hi Alex", captor.getValue().getSubject());
    assertEquals("Hello Alex", captor.getValue().getBody());
    assertEquals("HIGH", captor.getValue().getPriority());
    verify(emailService, never()).sendEmail(any(), any(), any());
  }

  @Test
  void handleShouldDispatchPushToItsOwnHighPriorityTopic() {
    NodeDef node = notificationNode(Map.of("templateId", 5, "channel", "push"));
    when(notificationTemplateRepository.findById(5L)).thenReturn(Optional.of(template(5L, "PUSH")));
    Map<String, Object> context = new HashMap<>(Map.of("userId", "device-123"));

    NodeOutcome outcome = handler.handle(execution, node, context);

    assertEquals(NodeOutcome.Type.CONTINUE, outcome.getType());
    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_PUSH_HIGH), eq("device-123"), any());
  }

  @Test
  void handleShouldResolveTheRecipientFromAConfiguredDotPathBeforeFallingBackToDefaultKeys() {
    NodeDef node = notificationNode(Map.of("templateId", 5, "recipientPath", "user.phone"));
    when(notificationTemplateRepository.findById(5L)).thenReturn(Optional.of(template(5L, "SMS")));
    Map<String, Object> context = new HashMap<>();
    context.put("user", Map.of("phone", "+15559998888"));
    context.put("recipientId", "should-be-ignored");

    handler.handle(execution, node, context);

    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_SMS_HIGH), eq("+15559998888"), any());
  }

  @Test
  void handleShouldThrowForAnUnsupportedNonEmailChannel() {
    NodeDef node = notificationNode(Map.of("templateId", 5, "channel", "fax"));
    when(notificationTemplateRepository.findById(5L)).thenReturn(Optional.of(template(5L, "FAX")));
    Map<String, Object> context = new HashMap<>(Map.of("userId", "u1"));

    assertThrows(IllegalStateException.class, () -> handler.handle(execution, node, context));
  }

  @Test
  void handleShouldSendEmailSynchronouslyAndRecordDeliveredWhenNoPreferenceBlocksIt() {
    NodeDef node = notificationNode(Map.of("templateId", 5));
    when(notificationTemplateRepository.findById(5L)).thenReturn(Optional.of(template(5L, "EMAIL")));
    when(userPreferenceRepository.findByUserId("user@example.com")).thenReturn(Optional.empty());
    Map<String, Object> context = new HashMap<>();
    context.put("email", "user@example.com");
    context.put("user", Map.of("firstName", "Alex"));

    NodeOutcome outcome = handler.handle(execution, node, context);

    assertEquals(NodeOutcome.Type.CONTINUE, outcome.getType());
    assertEquals("Email envoyé à user@example.com", outcome.getLogMessage());
    // Note: ContextUtils.set("email.sent", true) turns context["email"] from
    // the recipient string it started as into a nested {"sent": true} map —
    // the recipient itself was already resolved before this happens, so
    // dispatch is unaffected, but this is why we assert on the nested map
    // here rather than the original string.
    assertEquals(Boolean.TRUE, ((Map<?, ?>) context.get("email")).get("sent"));

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendEmail(eq("user@example.com"), eq("Hi Alex"), bodyCaptor.capture());
    assertTrue(bodyCaptor.getValue().contains("Hello Alex"));
    assertTrue(bodyCaptor.getValue().contains("http://localhost:8082/api/tracking/open/42"));

    verify(notificationLogService).record(any(), eq("user@example.com"), eq("EMAIL"), eq(NotificationLogService.STATUS_DELIVERED));
    verify(kafkaTemplate, never()).send(any(), any(), any());
  }

  @Test
  void handleShouldSuppressEmailWhenThePreferenceHasEmailDisabled() {
    NodeDef node = notificationNode(Map.of("templateId", 5));
    when(notificationTemplateRepository.findById(5L)).thenReturn(Optional.of(template(5L, "EMAIL")));
    UserPreference disabled = UserPreference.builder().userId("user@example.com").enabledEmail(false).build();
    when(userPreferenceRepository.findByUserId("user@example.com")).thenReturn(Optional.of(disabled));
    Map<String, Object> context = new HashMap<>(Map.of("email", "user@example.com"));

    NodeOutcome outcome = handler.handle(execution, node, context);

    assertEquals(NodeOutcome.Type.CONTINUE, outcome.getType());
    assertTrue(outcome.getLogMessage().contains("ignoré (désactivé"));
    verify(emailService, never()).sendEmail(any(), any(), any());
    verify(notificationLogService).record(any(), eq("user@example.com"), eq("EMAIL"), eq(NotificationLogService.STATUS_SUPPRESSED));
  }

  @Test
  void handleShouldSuppressEmailDuringTheConfiguredQuietHoursWindow() {
    NodeDef node = notificationNode(Map.of("templateId", 5));
    when(notificationTemplateRepository.findById(5L)).thenReturn(Optional.of(template(5L, "EMAIL")));
    // A window spanning the entire day guarantees "now" always falls inside
    // it, regardless of what time this test actually runs.
    UserPreference quiet = UserPreference.builder().userId("user@example.com").enabledEmail(true)
      .quietHoursStart(LocalTime.MIN).quietHoursEnd(LocalTime.MAX).build();
    when(userPreferenceRepository.findByUserId("user@example.com")).thenReturn(Optional.of(quiet));
    Map<String, Object> context = new HashMap<>(Map.of("email", "user@example.com"));

    NodeOutcome outcome = handler.handle(execution, node, context);

    assertEquals(NodeOutcome.Type.CONTINUE, outcome.getType());
    assertTrue(outcome.getLogMessage().contains("ignoré (heures calmes)"));
    verify(emailService, never()).sendEmail(any(), any(), any());
    verify(notificationLogService).record(any(), eq("user@example.com"), eq("EMAIL"), eq(NotificationLogService.STATUS_SUPPRESSED));
  }

  @Test
  void handleShouldFailTheNodeAndRecordFailedWhenEmailServiceThrows() {
    NodeDef node = notificationNode(Map.of("templateId", 5));
    when(notificationTemplateRepository.findById(5L)).thenReturn(Optional.of(template(5L, "EMAIL")));
    when(userPreferenceRepository.findByUserId("user@example.com")).thenReturn(Optional.empty());
    org.mockito.Mockito.doThrow(new RuntimeException("SMTP connection refused"))
      .when(emailService).sendEmail(any(), any(), any());
    Map<String, Object> context = new HashMap<>(Map.of("email", "user@example.com"));

    NodeOutcome outcome = handler.handle(execution, node, context);

    assertEquals(NodeOutcome.Type.FAIL, outcome.getType());
    assertTrue(outcome.getErrorMessage().contains("SMTP connection refused"));
    verify(notificationLogService).record(any(), eq("user@example.com"), eq("EMAIL"), eq(NotificationLogService.STATUS_FAILED));
  }
}
