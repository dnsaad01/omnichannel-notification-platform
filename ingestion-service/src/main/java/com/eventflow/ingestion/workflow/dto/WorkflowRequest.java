package com.eventflow.ingestion.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input DTO for POST/PUT /api/workflows. `definitionJson` is the raw
 * { nodes, edges } graph as a JSON string, authored via the Builder UI or
 * generated directly for testing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRequest {

  @NotBlank(message = "Workflow name is required")
  private String name;

  private String description;

  @NotBlank(message = "triggerEventType is required")
  private String triggerEventType;

  /** Defaults to an empty graph ({"nodes":[],"edges":[]}) when omitted, so a
   *  workflow can be created first and its graph filled in via a later PUT. */
  private String definitionJson;
}
