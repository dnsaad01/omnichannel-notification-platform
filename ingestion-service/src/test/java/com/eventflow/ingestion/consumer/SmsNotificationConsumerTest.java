package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.NotificationLogService;
import com.eventflow.ingestion.service.TwilioSmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsNotificationConsumerTest {

  @Mock
  private UserPreferenceRepository preferenceRepository;

  @Mock
  private TwilioSmsService smsService;

  @Mock
  private NotificationLogService notificationLogService;

  private SmsNotificationConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new SmsNotificationConsumer(preferenceRepository, smsService, notificationLogService);
  }

  private NotificationEvent.NotificationEventBuilder baseEvent() {
    return NotificationEvent.builder().eventId("evt-1").recipientId("+15550001111").priority("LOW");
  }

  @Test
  void consumeShouldSendAndRecordDeliveredWhenNoPreferenceBlocksIt() {
    when(preferenceRepository.findByUserId("+15550001111")).thenReturn(Optional.empty());
    NotificationEvent event = baseEvent().body("Your code is 1234").build();

    consumer.consume(event);

    verify(smsService).sendSms("+15550001111", "Your code is 1234");
    verify(notificationLogService).record("evt-1", "+15550001111", "SMS", NotificationLogService.STATUS_DELIVERED);
  }

  @Test
  void consumeShouldFallBackToThePayloadWhenBodyIsMissing() {
    when(preferenceRepository.findByUserId("+15550001111")).thenReturn(Optional.empty());
    NotificationEvent event = baseEvent().payload(java.util.Map.of("cartValue", 89.9)).build();

    consumer.consume(event);

    verify(smsService).sendSms(org.mockito.ArgumentMatchers.eq("+15550001111"), any());
  }

  @Test
  void consumeShouldSuppressAndRecordWhenSmsIsDisabledInPreferences() {
    UserPreference disabled = UserPreference.builder().userId("+15550001111").enabledSms(false).build();
    when(preferenceRepository.findByUserId("+15550001111")).thenReturn(Optional.of(disabled));
    NotificationEvent event = baseEvent().build();

    consumer.consume(event);

    verify(smsService, never()).sendSms(any(), any());
    verify(notificationLogService).record("evt-1", "+15550001111", "SMS", NotificationLogService.STATUS_SUPPRESSED);
  }

  @Test
  void consumeShouldSuppressALowPriorityMessageDuringQuietHours() {
    UserPreference quiet = UserPreference.builder().userId("+15550001111").enabledSms(true)
      .quietHoursStart(LocalTime.MIN).quietHoursEnd(LocalTime.MAX).build();
    when(preferenceRepository.findByUserId("+15550001111")).thenReturn(Optional.of(quiet));
    NotificationEvent event = baseEvent().priority("LOW").build();

    consumer.consume(event);

    verify(smsService, never()).sendSms(any(), any());
    verify(notificationLogService).record("evt-1", "+15550001111", "SMS", NotificationLogService.STATUS_SUPPRESSED);
  }

  @Test
  void consumeShouldBypassQuietHoursForAHighPriorityMessage() {
    UserPreference quiet = UserPreference.builder().userId("+15550001111").enabledSms(true)
      .quietHoursStart(LocalTime.MIN).quietHoursEnd(LocalTime.MAX).build();
    when(preferenceRepository.findByUserId("+15550001111")).thenReturn(Optional.of(quiet));
    NotificationEvent event = baseEvent().priority("HIGH").body("Urgent code: 9999").build();

    consumer.consume(event);

    verify(smsService).sendSms("+15550001111", "Urgent code: 9999");
    verify(notificationLogService).record("evt-1", "+15550001111", "SMS", NotificationLogService.STATUS_DELIVERED);
  }

  @Test
  void consumeShouldRecordFailedWhenTheSmsServiceThrows() {
    when(preferenceRepository.findByUserId("+15550001111")).thenReturn(Optional.empty());
    doThrow(new RuntimeException("Twilio error")).when(smsService).sendSms(any(), any());
    NotificationEvent event = baseEvent().body("Hello").build();

    consumer.consume(event);

    verify(notificationLogService).record("evt-1", "+15550001111", "SMS", NotificationLogService.STATUS_FAILED);
  }
}
