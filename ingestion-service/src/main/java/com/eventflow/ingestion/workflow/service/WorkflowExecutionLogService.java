package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.workflow.model.LogLevel;
import com.eventflow.ingestion.workflow.model.WorkflowExecutionLog;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Appends one line to an execution's Timeline. Mirrors the existing
 * NotificationLogService pattern (service/NotificationLogService.java):
 * a logging failure must never take down the engine step that triggered it,
 * so writes here are best-effort and swallow their own exceptions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionLogService {

  private final WorkflowExecutionLogRepository workflowExecutionLogRepository;

  public void info(Long executionId, String nodeId, String nodeType, String message) {
    append(executionId, nodeId, nodeType, message, LogLevel.INFO);
  }

  public void error(Long executionId, String nodeId, String nodeType, String message) {
    append(executionId, nodeId, nodeType, message, LogLevel.ERROR);
  }

  public void append(Long executionId, String nodeId, String nodeType, String message, LogLevel level) {
    try {
      workflowExecutionLogRepository.save(WorkflowExecutionLog.builder()
        .executionId(executionId)
        .nodeId(nodeId)
        .nodeType(nodeType)
        .message(message)
        .level(level)
        .build());
    } catch (Exception e) {
      log.warn("Failed to persist workflow execution log for execution [{}]: {}", executionId, e.getMessage());
    }
  }
}
