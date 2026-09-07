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
public class WorkflowResponse {
  private Long id;
  private String name;
  private String description;
  private String status;
  private String triggerEventType;
  private Integer version;
  private Long parentWorkflowId;
  private String definitionJson;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
