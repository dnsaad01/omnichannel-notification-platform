package com.eventflow.ingestion.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Used for both the executions list (logs omitted — null) and the execution
 * detail endpoint (logs populated), so the frontend's Executions and
 * Execution Detail pages can share one response shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutionResponse {
  private Long id;
  private Long workflowId;
  private Integer workflowVersion;
  private String status;
  private String currentNodeId;
  private String contextJson;
  private LocalDateTime nextWakeAt;
  private LocalDateTime startedAt;
  private LocalDateTime lastActivityAt;
  private LocalDateTime completedAt;
  private List<WorkflowExecutionLogResponse> logs;
}
