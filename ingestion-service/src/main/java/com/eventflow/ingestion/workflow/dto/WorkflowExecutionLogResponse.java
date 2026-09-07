package com.eventflow.ingestion.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutionLogResponse {
  private Long id;
  private String nodeId;
  private String nodeType;
  private String message;
  private String level;
  private LocalDateTime createdAt;
}
