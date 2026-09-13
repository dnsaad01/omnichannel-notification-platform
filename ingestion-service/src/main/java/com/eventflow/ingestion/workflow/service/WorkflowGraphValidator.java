package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.workflow.engine.graph.EdgeDef;
import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.engine.graph.WorkflowGraph;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural validation run by WorkflowService#activate before a workflow is
 * allowed to go ACTIVE:
 * - exactly one TRIGGER node
 * - every node reachable from the trigger
 * - every GATEWAY has exactly one "yes" and one "no" outgoing edge
 * - every non-GATEWAY, non-END node has exactly one outgoing edge
 * - END nodes have no outgoing edges
 * - no edge references a node id that doesn't exist
 *
 * Collects every violation in one pass (rather than throwing on the first)
 * so the caller can fix them all at once.
 */
@Component
public class WorkflowGraphValidator {

  private static final Set<String> KNOWN_TYPES = Set.of("TRIGGER", "NOTIFICATION", "WAIT", "GATEWAY", "END");

  public List<String> validate(WorkflowGraph graph) {
    List<String> errors = new ArrayList<>();

    if (graph.getNodes() == null || graph.getNodes().isEmpty()) {
      errors.add("The workflow graph has no nodes");
      return errors;
    }

    Set<String> nodeIds = new HashSet<>();
    for (NodeDef node : graph.getNodes()) {
      if (node.getId() == null || node.getId().isBlank()) {
        errors.add("A node is missing an id");
        continue;
      }
      if (!nodeIds.add(node.getId())) {
        errors.add("Duplicate node id: " + node.getId());
      }
      if (node.getType() == null || !KNOWN_TYPES.contains(node.getType().toUpperCase())) {
        errors.add("Node [" + node.getId() + "] has an unknown type: " + node.getType());
      }
    }

    List<NodeDef> triggers = graph.findNodesByType("TRIGGER");
    if (triggers.isEmpty()) {
      errors.add("The workflow has no TRIGGER node");
    } else if (triggers.size() > 1) {
      errors.add("The workflow has more than one TRIGGER node");
    }

    for (EdgeDef edge : graph.getEdges()) {
      if (edge.getSource() == null || !nodeIds.contains(edge.getSource())) {
        errors.add("Edge [" + edge.getId() + "] has a dangling source: " + edge.getSource());
      }
      if (edge.getTarget() == null || !nodeIds.contains(edge.getTarget())) {
        errors.add("Edge [" + edge.getId() + "] has a dangling target: " + edge.getTarget());
      }
    }

    for (NodeDef node : graph.getNodes()) {
      if (node.getType() == null) {
        continue;
      }
      List<EdgeDef> outgoing = graph.outgoingEdges(node.getId());
      switch (node.getType().toUpperCase()) {
        case "GATEWAY" -> {
          long yes = outgoing.stream().filter(e -> "yes".equalsIgnoreCase(e.getSourceHandle())).count();
          long no = outgoing.stream().filter(e -> "no".equalsIgnoreCase(e.getSourceHandle())).count();
          if (yes != 1 || no != 1) {
            errors.add("GATEWAY node [" + node.getId() + "] must have exactly one 'yes' and one 'no' outgoing edge (found " + yes + " yes, " + no + " no)");
          }
        }
        case "END" -> {
          if (!outgoing.isEmpty()) {
            errors.add("END node [" + node.getId() + "] must not have outgoing edges");
          }
        }
        default -> {
          if (outgoing.size() != 1) {
            errors.add("Node [" + node.getId() + "] (" + node.getType() + ") must have exactly one outgoing edge (found " + outgoing.size() + ")");
          }
        }
      }
    }

    if (!triggers.isEmpty() && errors.isEmpty()) {
      errors.addAll(checkReachability(graph, triggers.get(0), nodeIds));
    }

    return errors;
  }

  private List<String> checkReachability(WorkflowGraph graph, NodeDef trigger, Set<String> allNodeIds) {
    Set<String> visited = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    queue.add(trigger.getId());
    visited.add(trigger.getId());

    while (!queue.isEmpty()) {
      String current = queue.poll();
      for (EdgeDef edge : graph.outgoingEdges(current)) {
        if (visited.add(edge.getTarget())) {
          queue.add(edge.getTarget());
        }
      }
    }

    List<String> errors = new ArrayList<>();
    for (String nodeId : allNodeIds) {
      if (!visited.contains(nodeId)) {
        errors.add("Node [" + nodeId + "] is not reachable from the TRIGGER node");
      }
    }
    return errors;
  }
}
