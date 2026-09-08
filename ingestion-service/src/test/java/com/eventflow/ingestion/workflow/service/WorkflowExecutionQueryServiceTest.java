package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.exception.WorkflowExecutionNotFoundException;
import com.eventflow.ingestion.workflow.dto.WorkflowExecutionResponse;
import com.eventflow.ingestion.workflow.model.ExecutionStatus;
import com.eventflow.ingestion.workflow.model.LogLevel;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import com.eventflow.ingestion.workflow.model.WorkflowExecutionLog;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionLogRepository;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionQueryServiceTest {

  @Mock
  private WorkflowExecutionRepository workflowExecutionRepository;

  @Mock
  private WorkflowExecutionLogRepository workflowExecutionLogRepository;

  private WorkflowExecutionQueryService workflowExecutionQueryService;

  @BeforeEach
  void setUp() {
    workflowExecutionQueryService = new WorkflowExecutionQueryService(workflowExecutionRepository, workflowExecutionLogRepository);
  }

  private WorkflowExecution anExecution() {
    return WorkflowExecution.builder()
      .id(10L)
      .workflowId(1L)
      .workflowVersion(1)
      .status(ExecutionStatus.RUNNING)
      .currentNodeId("node-2")
      .contextJson("{}")
      .build();
  }

  @Test
  void findByWorkflowIdShouldMapEachExecutionToASummaryWithoutLogs() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<WorkflowExecution> page = new PageImpl<>(List.of(anExecution()), pageable, 1);
    when(workflowExecutionRepository.findByWorkflowId(1L, pageable)).thenReturn(page);

    Page<WorkflowExecutionResponse> result = workflowExecutionQueryService.findByWorkflowId(1L, pageable);

    assertEquals(1, result.getTotalElements());
    WorkflowExecutionResponse summary = result.getContent().get(0);
    assertEquals(10L, summary.getId());
    assertEquals("RUNNING", summary.getStatus());
    assertEquals("node-2", summary.getCurrentNodeId());
  }

  @Test
  void findByStatusShouldDelegateToTheRepositoryWithTheGivenStatus() {
    Pageable pageable = PageRequest.of(0, 10);
    when(workflowExecutionRepository.findByStatus(ExecutionStatus.FAILED, pageable))
      .thenReturn(new PageImpl<>(List.of()));

    Page<WorkflowExecutionResponse> result = workflowExecutionQueryService.findByStatus(ExecutionStatus.FAILED, pageable);

    assertEquals(0, result.getTotalElements());
  }

  @Test
  void findDetailByIdShouldAttachLogsOrderedAsReturnedByTheRepository() {
    WorkflowExecution execution = anExecution();
    when(workflowExecutionRepository.findById(10L)).thenReturn(Optional.of(execution));

    WorkflowExecutionLog log1 = WorkflowExecutionLog.builder()
      .id(1L).executionId(10L).nodeId(null).nodeType("TRIGGER").message("Event reçu").level(LogLevel.INFO).build();
    WorkflowExecutionLog log2 = WorkflowExecutionLog.builder()
      .id(2L).executionId(10L).nodeId("node-1").nodeType("NOTIFICATION").message("Email envoyé").level(LogLevel.INFO).build();
    when(workflowExecutionLogRepository.findByExecutionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(log1, log2));

    WorkflowExecutionResponse detail = workflowExecutionQueryService.findDetailById(10L);

    assertEquals(10L, detail.getId());
    assertEquals(2, detail.getLogs().size());
    assertEquals("Event reçu", detail.getLogs().get(0).getMessage());
    assertEquals("INFO", detail.getLogs().get(0).getLevel());
    assertEquals("Email envoyé", detail.getLogs().get(1).getMessage());
  }

  @Test
  void findDetailByIdShouldThrowWorkflowExecutionNotFoundExceptionForAnUnknownId() {
    when(workflowExecutionRepository.findById(999L)).thenReturn(Optional.empty());

    assertThrows(WorkflowExecutionNotFoundException.class,
      () -> workflowExecutionQueryService.findDetailById(999L));
  }
}
