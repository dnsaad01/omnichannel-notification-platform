package com.eventflow.ingestion.workflow.engine.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory read model of a Workflow.definitionJson blob — { nodes, edges }.
 * Owns its own ObjectMapper rather than having one injected: this class is
 * used both by Spring-managed engine beans and (deliberately) is trivial to
 * unit-test with a plain `new WorkflowGraph()` / `WorkflowGraph.parse(json)`
 * call, with no Spring context needed. A plain Jackson ObjectMapper is
 * thread-safe and stateless once configured, so one static instance here is
 * safe to share across every advance() call.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowGraph {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private List<NodeDef> nodes = new ArrayList<>();
  private List<EdgeDef> edges = new ArrayList<>();

  public static WorkflowGraph parse(String definitionJson) {
    if (definitionJson == null || definitionJson.isBlank()) {
      return new WorkflowGraph();
    }
    try {
      return MAPPER.readValue(definitionJson, WorkflowGraph.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Malformed workflow definitionJson: " + e.getMessage(), e);
    }
  }

  public String serialize() {
    try {
      return MAPPER.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize workflow graph: " + e.getMessage(), e);
    }
  }

  public Optional<NodeDef> findNode(String nodeId) {
    return nodes.stream().filter(n -> n.getId() != null && n.getId().equals(nodeId)).findFirst();
  }

  public Optional<NodeDef> findTriggerNode() {
    return nodes.stream().filter(n -> "TRIGGER".equalsIgnoreCase(n.getType())).findFirst();
  }

  public List<NodeDef> findNodesByType(String type) {
    return nodes.stream().filter(n -> type.equalsIgnoreCase(n.getType())).toList();
  }

  public List<EdgeDef> outgoingEdges(String nodeId) {
    return edges.stream().filter(e -> nodeId.equals(e.getSource())).toList();
  }

  /**
   * The edge to follow when leaving {@code nodeId}. When {@code branch} is
   * null, there must be exactly one outgoing edge (any non-GATEWAY node) —
   * the first (only) one found is returned. When {@code branch} is
   * "yes"/"no", only the outgoing edge whose sourceHandle matches is
   * eligible (GATEWAY nodes only).
   */
  public Optional<EdgeDef> outgoingEdge(String nodeId, String branch) {
    List<EdgeDef> candidates = outgoingEdges(nodeId);
    if (branch == null) {
      return candidates.stream().findFirst();
    }
    return candidates.stream().filter(e -> branch.equalsIgnoreCase(e.getSourceHandle())).findFirst();
  }
}
