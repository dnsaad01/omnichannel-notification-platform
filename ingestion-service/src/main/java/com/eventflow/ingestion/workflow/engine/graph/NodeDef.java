package com.eventflow.ingestion.workflow.engine.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * One node in a Workflow's definitionJson graph. This is a plain in-memory
 * read model for the engine — it is deserialized fresh from the jsonb column
 * on every advance (WorkflowGraph.parse), never persisted on its own.
 *
 * `position` is kept only so re-serializing a graph (e.g. after a future
 * builder-side edit) doesn't silently drop the canvas layout the frontend
 * cares about — the engine itself never reads it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeDef {

  private String id;

  /** TRIGGER | NOTIFICATION | WAIT | GATEWAY | END */
  private String type;

  private String name;

  @Builder.Default
  private Map<String, Object> config = new HashMap<>();

  @Builder.Default
  private Map<String, Object> position = new HashMap<>();
}
