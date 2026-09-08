package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dto.NotificationResendResponse;
import com.eventflow.ingestion.exception.NotificationLogNotFoundException;
import com.eventflow.ingestion.model.NotificationLog;
import com.eventflow.ingestion.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the id-parsing and not-found branching in NotificationResendService
 * — the piece that turns "Relancer la notification" from a client-side fake
 * into a real backend action. See that class's own doc comment for the full
 * root-cause explanation.
 */
@ExtendWith(MockitoExtension.class)
class NotificationResendServiceTest {

  @Mock
  private NotificationLogRepository notificationLogRepository;

  @Mock
  private NotificationIngestionService notificationIngestionService;

  private NotificationResendService notificationResendService;

  @BeforeEach
  void setUp() {
    notificationResendService = new NotificationResendService(notificationLogRepository, notificationIngestionService);
  }

  private NotificationLog aLog(long id) {
    return NotificationLog.builder()
      .id(id)
      .eventId("evt-" + id)
      .recipientId("user@test.com")
      .channel("EMAIL")
      .status("FAILED")
      .createdAt(LocalDateTime.now())
      .build();
  }

  @Test
  void shouldPublishAResendAndReturnAConfirmationWhenTheLogExists() {
    when(notificationLogRepository.findById(42L)).thenReturn(Optional.of(aLog(42L)));

    NotificationResendResponse response = notificationResendService.resend("NOTIF-42");

    verify(notificationIngestionService).publishResend("user@test.com", "EMAIL", "evt-42");
    assertEquals("NOTIF-42", response.getOriginalId());
    assertEquals("user@test.com", response.getRecipient());
    assertEquals("EMAIL", response.getChannel());
  }

  @Test
  void shouldAcceptABareNumericIdWithoutThePrefix() {
    when(notificationLogRepository.findById(7L)).thenReturn(Optional.of(aLog(7L)));

    notificationResendService.resend("7");

    verify(notificationIngestionService).publishResend("user@test.com", "EMAIL", "evt-7");
  }

  @Test
  void shouldThrowNotFoundWhenTheLogDoesNotExist() {
    when(notificationLogRepository.findById(999L)).thenReturn(Optional.empty());

    assertThrows(NotificationLogNotFoundException.class,
      () -> notificationResendService.resend("NOTIF-999"));

    verify(notificationIngestionService, never()).publishResend(any(), any(), any());
  }

  @Test
  void shouldThrowNotFoundWhenTheIdIsNotNumeric() {
    assertThrows(NotificationLogNotFoundException.class,
      () -> notificationResendService.resend("NOTIF-abc"));

    verify(notificationIngestionService, never()).publishResend(any(), any(), any());
    verify(notificationLogRepository, never()).findById(any());
  }

  @Test
  void shouldThrowNotFoundWhenTheIdIsBlank() {
    assertThrows(NotificationLogNotFoundException.class,
      () -> notificationResendService.resend("  "));

    verify(notificationIngestionService, never()).publishResend(any(), any(), any());
  }
}
