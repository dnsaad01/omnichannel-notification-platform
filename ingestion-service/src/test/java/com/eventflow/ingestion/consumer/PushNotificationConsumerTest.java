package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import com.eventflow.ingestion.service.FcmPushService;
import com.eventflow.ingestion.service.NotificationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificationConsumerTest {

  @Mock
  private UserPreferenceRepository preferenceRepository;

  @Mock
  private FcmPushService pushService;

  @Mock
  private NotificationLogService notificationLogService;

  private PushNotificationConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new PushNotificationConsumer(preferenceRepository, pushService, notificationLogService);
  }

  private NotificationEvent.NotificationEventBuilder baseEvent() {
    return NotificationEvent.builder().eventId("evt-1").recipientId("device-token-1").priority("LOW");
  }

  @Test
  void consumeShouldSendAndRecordDeliveredWhenNoPreferenceBlocksIt() {
    when(preferenceRepository.findByUserId("device-token-1")).thenReturn(Optional.empty());
    NotificationEvent event = baseEvent().subject("Sale!").body("50% off today").payload(Map.of("cartId", "c1")).build();

    consumer.consume(event);

    ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
    verify(pushService).sendPushNotification(eq("device-token-1"), eq("Sale!"), eq("50% off today"), dataCaptor.capture());
    assertEquals("c1", dataCaptor.getValue().get("cartId"));
    verify(notificationLogService).record("evt-1", "device-token-1", "PUSH", NotificationLogService.STATUS_DELIVERED);
  }

  @Test
  void consumeShouldDefaultTitleAndBodyAndToleratesANullPayload() {
    when(preferenceRepository.findByUserId("device-token-1")).thenReturn(Optional.empty());
    NotificationEvent event = baseEvent().build();

    consumer.consume(event);

    verify(pushService).sendPushNotification(eq("device-token-1"), eq("Notification Alert"),
      eq("You have a new alert notification"), eq(Map.of()));
  }

  @Test
  void consumeShouldSuppressAndRecordWhenPushIsDisabledInPreferences() {
    UserPreference disabled = UserPreference.builder().userId("device-token-1").enabledPush(false).build();
    when(preferenceRepository.findByUserId("device-token-1")).thenReturn(Optional.of(disabled));
    NotificationEvent event = baseEvent().build();

    consumer.consume(event);

    verify(pushService, never()).sendPushNotification(any(), any(), any(), any());
    verify(notificationLogService).record("evt-1", "device-token-1", "PUSH", NotificationLogService.STATUS_SUPPRESSED);
  }

  @Test
  void consumeShouldSuppressALowPriorityMessageDuringQuietHours() {
    UserPreference quiet = UserPreference.builder().userId("device-token-1").enabledPush(true)
      .quietHoursStart(LocalTime.MIN).quietHoursEnd(LocalTime.MAX).build();
    when(preferenceRepository.findByUserId("device-token-1")).thenReturn(Optional.of(quiet));
    NotificationEvent event = baseEvent().priority("LOW").build();

    consumer.consume(event);

    verify(pushService, never()).sendPushNotification(any(), any(), any(), any());
    verify(notificationLogService).record("evt-1", "device-token-1", "PUSH", NotificationLogService.STATUS_SUPPRESSED);
  }

  @Test
  void consumeShouldBypassQuietHoursForAHighPriorityMessage() {
    UserPreference quiet = UserPreference.builder().userId("device-token-1").enabledPush(true)
      .quietHoursStart(LocalTime.MIN).quietHoursEnd(LocalTime.MAX).build();
    when(preferenceRepository.findByUserId("device-token-1")).thenReturn(Optional.of(quiet));
    NotificationEvent event = baseEvent().priority("HIGH").subject("Urgent").body("Act now").build();

    consumer.consume(event);

    verify(pushService).sendPushNotification(eq("device-token-1"), eq("Urgent"), eq("Act now"), any());
    verify(notificationLogService).record("evt-1", "device-token-1", "PUSH", NotificationLogService.STATUS_DELIVERED);
  }

  @Test
  void consumeShouldRecordFailedWhenThePushServiceThrows() {
    when(preferenceRepository.findByUserId("device-token-1")).thenReturn(Optional.empty());
    doThrow(new RuntimeException("FCM error")).when(pushService).sendPushNotification(any(), any(), any(), any());
    NotificationEvent event = baseEvent().build();

    consumer.consume(event);

    verify(notificationLogService).record("evt-1", "device-token-1", "PUSH", NotificationLogService.STATUS_FAILED);
  }
}
