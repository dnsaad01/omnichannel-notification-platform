package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.exception.WorkflowExecutionNotFoundException;
import com.eventflow.ingestion.workflow.dto.WorkflowExecutionLogResponse;
import com.eventflow.ingestion.workflow.dto.WorkflowExecutionResponse;
import com.eventflow.ingestion.workflow.model.ExecutionStatus;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import com.eventflow.ingestion.workflow.model.WorkflowExecutionLog;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionLogRepository;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Read-only access to WorkflowExecutions for the Exécutions list/detail
 * pages, and the only way to observe the engine working end-to-end via
 * curl.
 */
@Service
@RequiredArgsConstructor
public class WorkflowExecutionQueryService {

  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final WorkflowExecutionLogRepository workflowExecutionLogRepository;

  public Page<WorkflowExecutionResponse> findByWorkflowId(Long workflowId, Pageable pageable) {
    return workflowExecutionRepository.findByWorkflowId(workflowId, pageable).map(this::toSummary);
  }

  public Page<WorkflowExecutionResponse> findByStatus(ExecutionStatus status, Pageable pageable) {
    return workflowExecutionRepository.findByStatus(status, pageable).map(this::toSummary);
  }

  public Page<WorkflowExecutionResponse> findAll(Pageable pageable) {
    return workflowExecutionRepository.findAll(pageable).map(this::toSummary);
  }

  public WorkflowExecutionResponse findDetailById(Long id) {
    WorkflowExecution execution = workflowExecutionRepository.findById(id)
      .orElseThrow(() -> new WorkflowExecutionNotFoundException(id));

    WorkflowExecutionResponse response = toSummary(execution);
    response.setLogs(
      workflowExecutionLogRepository.findByExecutionIdOrderByCreatedAtAsc(id).stream()
        .map(this::toLogResponse)
        .toList()
    );
    return response;
  }

  private WorkflowExecutionResponse toSummary(WorkflowExecution execution) {
    return WorkflowExecutionResponse.builder()
      .id(execution.getId())
      .workflowId(execution.getWorkflowId())
      .workflowVersion(execution.getWorkflowVersion())
      .status(execution.getStatus().name())
      .currentNodeId(execution.getCurrentNodeId())
      .contextJson(execution.getContextJson())
      .nextWakeAt(execution.getNextWakeAt())
      .startedAt(execution.getStartedAt())
      .lastActivityAt(execution.getLastActivityAt())
      .completedAt(execution.getCompletedAt())
      .build();
  }

  private WorkflowExecutionLogResponse toLogResponse(WorkflowExecutionLog logEntry) {
    return WorkflowExecutionLogResponse.builder()
      .id(logEntry.getId())
      .nodeId(logEntry.getNodeId())
      .nodeType(logEntry.getNodeType())
      .message(logEntry.getMessage())
      .level(logEntry.getLevel().name())
      .createdAt(logEntry.getCreatedAt())
      .build();
  }
}
