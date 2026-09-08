package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.EmailService;
import com.eventflow.ingestion.service.NotificationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This is the async fire-and-forget EMAIL path fed by
 * NotificationIngestionService and (for SMS/PUSH, its sibling consumers)
 * still used today — distinct from NotificationNodeHandler's own
 * synchronous EMAIL dispatch for workflow-originated notifications (see
 * that class's own extensive doc comment on why the two paths diverged).
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationConsumerTest {

  @Mock
  private UserPreferenceRepository preferenceRepository;

  @Mock
  private EmailService emailService;

  @Mock
  private NotificationLogService notificationLogService;

  private EmailNotificationConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new EmailNotificationConsumer(preferenceRepository, emailService, notificationLogService);
  }

  private NotificationEvent.NotificationEventBuilder baseEvent() {
    return NotificationEvent.builder().eventId("evt-1").recipientId("user@example.com").priority("LOW");
  }

  @Test
  void consumeShouldSendAndRecordDeliveredWhenNoPreferenceBlocksIt() {
    when(preferenceRepository.findByUserId("user@example.com")).thenReturn(Optional.empty());
    NotificationEvent event = baseEvent().subject("Hi").body("Hello").build();

    consumer.consume(event);

    verify(emailService).sendEmail("user@example.com", "Hi", "Hello");
    verify(notificationLogService).record("evt-1", "user@example.com", "EMAIL", NotificationLogService.STATUS_DELIVERED);
  }

  @Test
  void consumeShouldDefaultTheSubjectAndFallBackToThePayloadWhenBodyIsMissing() {
    when(preferenceRepository.findByUserId("user@example.com")).thenReturn(Optional.empty());
    NotificationEvent event = baseEvent().payload(java.util.Map.of("cartValue", 89.9)).build();

    consumer.consume(event);

    verify(emailService).sendEmail(eq("user@example.com"), eq("Notification Alert"), any());
  }

  @Test
  void consumeShouldSuppressAndRecordWhenEmailIsDisabledInPreferences() {
    UserPreference disabled = UserPreference.builder().userId("user@example.com").enabledEmail(false).build();
    when(preferenceRepository.findByUserId("user@example.com")).thenReturn(Optional.of(disabled));
    NotificationEvent event = baseEvent().build();

    consumer.consume(event);

    verify(emailService, never()).sendEmail(any(), any(), any());
    verify(notificationLogService).record("evt-1", "user@example.com", "EMAIL", NotificationLogService.STATUS_SUPPRESSED);
  }

  @Test
  void consumeShouldSuppressALowPriorityMessageDuringQuietHours() {
    UserPreference quiet = UserPreference.builder().userId("user@example.com").enabledEmail(true)
      .quietHoursStart(LocalTime.MIN).quietHoursEnd(LocalTime.MAX).build();
    when(preferenceRepository.findByUserId("user@example.com")).thenReturn(Optional.of(quiet));
    NotificationEvent event = baseEvent().priority("LOW").build();

    consumer.consume(event);

    verify(emailService, never()).sendEmail(any(), any(), any());
    verify(notificationLogService).record("evt-1", "user@example.com", "EMAIL", NotificationLogService.STATUS_SUPPRESSED);
  }

  @Test
  void consumeShouldBypassQuietHoursForAHighPriorityMessage() {
    UserPreference quiet = UserPreference.builder().userId("user@example.com").enabledEmail(true)
      .quietHoursStart(LocalTime.MIN).quietHoursEnd(LocalTime.MAX).build();
    when(preferenceRepository.findByUserId("user@example.com")).thenReturn(Optional.of(quiet));
    NotificationEvent event = baseEvent().priority("HIGH").subject("Urgent").body("Act now").build();

    consumer.consume(event);

    verify(emailService).sendEmail("user@example.com", "Urgent", "Act now");
    verify(notificationLogService).record("evt-1", "user@example.com", "EMAIL", NotificationLogService.STATUS_DELIVERED);
  }

  @Test
  void consumeShouldRecordFailedWhenTheEmailServiceThrows() {
    when(preferenceRepository.findByUserId("user@example.com")).thenReturn(Optional.empty());
    doThrow(new RuntimeException("SMTP down")).when(emailService).sendEmail(any(), any(), any());
    NotificationEvent event = baseEvent().subject("Hi").body("Hello").build();

    consumer.consume(event);

    verify(notificationLogService).record("evt-1", "user@example.com", "EMAIL", NotificationLogService.STATUS_FAILED);
  }
}
