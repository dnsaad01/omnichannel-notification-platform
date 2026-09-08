package com.eventflow.ingestion.service;

import com.eventflow.ingestion.model.NotificationLog;
import com.eventflow.ingestion.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationLogServiceTest {

  @Mock
  private NotificationLogRepository notificationLogRepository;

  private NotificationLogService notificationLogService;

  @BeforeEach
  void setUp() {
    notificationLogService = new NotificationLogService(notificationLogRepository);
  }

  @Test
  void shouldPersistOneAuditRowWithTheGivenFields() {
    notificationLogService.record("evt-1", "user@example.com", "EMAIL", NotificationLogService.STATUS_DELIVERED);

    ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
    verify(notificationLogRepository).save(captor.capture());

    NotificationLog saved = captor.getValue();
    assertEquals("evt-1", saved.getEventId());
    assertEquals("user@example.com", saved.getRecipientId());
    assertEquals("EMAIL", saved.getChannel());
    assertEquals("DELIVERED", saved.getStatus());
    assertNotNull(saved.getCreatedAt());
  }

  @Test
  void shouldSwallowRepositoryFailuresRatherThanPropagateThem() {
    // Per the class doc comment: "an audit-trail write must never take down
    // the actual notification delivery path" — a repository exception here
    // must never bubble up to whatever called record(...).
    when(notificationLogRepository.save(any())).thenThrow(new RuntimeException("DB connection lost"));

    assertDoesNotThrow(() ->
      notificationLogService.record("evt-2", "user@example.com", "EMAIL", NotificationLogService.STATUS_FAILED)
    );
  }
}
