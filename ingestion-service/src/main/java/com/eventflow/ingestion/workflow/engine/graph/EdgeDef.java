package com.eventflow.ingestion.workflow.engine.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One edge in a Workflow's definitionJson graph.
 *
 * `sourceHandle` is only meaningful when `source` is a GATEWAY node — it is
 * "yes" or "no", matching the branch GatewayNodeHandler evaluates. Every
 * other node type has exactly one outgoing edge, so sourceHandle is null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EdgeDef {

  private String id;
  private String source;
  private String sourceHandle;
  private String target;
}
