package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.workflow.model.LogLevel;
import com.eventflow.ingestion.workflow.model.WorkflowExecutionLog;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors NotificationLogServiceTest's own coverage shape for the sibling
 * "append-only audit trail" service in the workflow engine — including the
 * one behavior both classes share by design: a logging failure must never
 * propagate and take down the engine step that triggered it.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowExecutionLogServiceTest {

  @Mock
  private WorkflowExecutionLogRepository workflowExecutionLogRepository;

  private WorkflowExecutionLogService service;

  @BeforeEach
  void setUp() {
    service = new WorkflowExecutionLogService(workflowExecutionLogRepository);
  }

  @Test
  void infoShouldPersistALogRowAtInfoLevel() {
    service.info(1L, "n1", "NOTIFICATION", "Email envoyé");

    ArgumentCaptor<WorkflowExecutionLog> captor = ArgumentCaptor.forClass(WorkflowExecutionLog.class);
    verify(workflowExecutionLogRepository).save(captor.capture());
    WorkflowExecutionLog saved = captor.getValue();
    assertEquals(1L, saved.getExecutionId());
    assertEquals("n1", saved.getNodeId());
    assertEquals("NOTIFICATION", saved.getNodeType());
    assertEquals("Email envoyé", saved.getMessage());
    assertEquals(LogLevel.INFO, saved.getLevel());
  }

  @Test
  void errorShouldPersistALogRowAtErrorLevel() {
    service.error(2L, "g1", "GATEWAY", "Condition invalide");

    ArgumentCaptor<WorkflowExecutionLog> captor = ArgumentCaptor.forClass(WorkflowExecutionLog.class);
    verify(workflowExecutionLogRepository).save(captor.capture());
    assertEquals(LogLevel.ERROR, captor.getValue().getLevel());
  }

  @Test
  void infoShouldToleratesANullNodeIdAndNodeTypeForTheTwoSyntheticStartupEntries() {
    service.info(3L, null, null, "Événement reçu depuis Kafka : CART_ABANDONED");

    ArgumentCaptor<WorkflowExecutionLog> captor = ArgumentCaptor.forClass(WorkflowExecutionLog.class);
    verify(workflowExecutionLogRepository).save(captor.capture());
    assertNull(captor.getValue().getNodeId());
    assertNull(captor.getValue().getNodeType());
  }

  @Test
  void appendShouldSwallowARepositoryFailureRatherThanPropagateIt() {
    when(workflowExecutionLogRepository.save(any())).thenThrow(new RuntimeException("db down"));

    assertDoesNotThrow(() -> service.info(1L, "n1", "NOTIFICATION", "message"));
  }
}
