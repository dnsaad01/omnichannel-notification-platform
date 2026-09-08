package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A real Jackson ObjectMapper is used throughout rather than a mock — it's
 * pure, fast, and mocking JSON (de)serialization would mean re-implementing
 * Jackson's behavior in stubs instead of actually exercising it, which is
 * exactly the kind of "test" that passes without proving anything.
 */
@ExtendWith(MockitoExtension.class)
class EmailTrackingServiceTest {

  @Mock
  private WorkflowExecutionRepository workflowExecutionRepository;

  @Mock
  private WorkflowExecutionLogService executionLogService;

  private EmailTrackingService emailTrackingService;

  @BeforeEach
  void setUp() {
    emailTrackingService = new EmailTrackingService(workflowExecutionRepository, executionLogService, new ObjectMapper());
  }

  @Test
  void shouldMarkEmailOpenedAndLogItOnTheFirstOpen() throws Exception {
    WorkflowExecution execution = WorkflowExecution.builder()
      .id(7L)
      .contextJson("{\"recipientId\":\"user@example.com\"}")
      .build();
    when(workflowExecutionRepository.findById(7L)).thenReturn(Optional.of(execution));

    emailTrackingService.recordOpen(7L);

    ArgumentCaptor<WorkflowExecution> savedCaptor = ArgumentCaptor.forClass(WorkflowExecution.class);
    verify(workflowExecutionRepository).save(savedCaptor.capture());

    Map<?, ?> context = new ObjectMapper().readValue(savedCaptor.getValue().getContextJson(), Map.class);
    Map<?, ?> email = (Map<?, ?>) context.get("email");
    assertEquals(Boolean.TRUE, email.get("opened"));
    assertTrue(email.containsKey("openedAt"));

    // First-open transition (false/absent -> true) is the only case that
    // should write a timeline entry. nodeId is deliberately null in the
    // production call (see EmailTrackingService#recordOpen).
    verify(executionLogService).info(eq(7L), isNull(), eq("TRACKING"), anyString());
  }

  @Test
  void shouldNotLogAgainOnASecondOpenOfTheSameEmail() {
    WorkflowExecution execution = WorkflowExecution.builder()
      .id(7L)
      .contextJson("{\"email\":{\"opened\":true,\"openedAt\":\"2025-01-01T00:00:00\"}}")
      .build();
    when(workflowExecutionRepository.findById(7L)).thenReturn(Optional.of(execution));

    emailTrackingService.recordOpen(7L);

    // Already true before this call -> no *new* transition -> no log line,
    // even though the context is still re-saved (openedAt gets refreshed).
    verify(executionLogService, never()).info(any(), any(), any(), any());
    verify(workflowExecutionRepository, times(1)).save(any());
  }

  @Test
  void shouldStartFromAnEmptyContextWhenContextJsonIsNull() {
    WorkflowExecution execution = WorkflowExecution.builder().id(7L).contextJson(null).build();
    when(workflowExecutionRepository.findById(7L)).thenReturn(Optional.of(execution));

    assertDoesNotThrow(() -> emailTrackingService.recordOpen(7L));

    verify(workflowExecutionRepository).save(any());
  }

  @Test
  void shouldStartFromAnEmptyContextWhenContextJsonIsMalformed() {
    WorkflowExecution execution = WorkflowExecution.builder().id(7L).contextJson("{not valid json").build();
    when(workflowExecutionRepository.findById(7L)).thenReturn(Optional.of(execution));

    assertDoesNotThrow(() -> emailTrackingService.recordOpen(7L));

    verify(workflowExecutionRepository).save(any());
  }

  @Test
  void shouldSilentlyDoNothingWhenTheExecutionDoesNotExist() {
    when(workflowExecutionRepository.findById(999L)).thenReturn(Optional.empty());

    // The pixel must always render regardless of what happens here — see
    // the class doc comment — so an unknown execution id must never throw.
    assertDoesNotThrow(() -> emailTrackingService.recordOpen(999L));

    verify(workflowExecutionRepository, never()).save(any());
    verify(executionLogService, never()).info(any(), any(), any(), any());
  }

  @Test
  void shouldSwallowAnUnexpectedRepositoryFailureRatherThanPropagateIt() {
    when(workflowExecutionRepository.findById(7L)).thenThrow(new RuntimeException("DB unavailable"));

    assertDoesNotThrow(() -> emailTrackingService.recordOpen(7L));
  }
}
