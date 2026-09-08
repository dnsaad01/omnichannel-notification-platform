package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.workflow.engine.graph.WorkflowGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure, stateless graph validation — no mocking needed. WorkflowServiceTest
 * already exercises this indirectly (with the real validator, not a mock)
 * for the two rules WorkflowService's own activation flow cares about most;
 * this class instead drives every rule directly and in isolation, including
 * several WorkflowServiceTest never reaches (duplicate ids, unknown types,
 * dangling edges, GATEWAY branch-count rules, unreachable nodes).
 */
class WorkflowGraphValidatorTest {

  private final WorkflowGraphValidator validator = new WorkflowGraphValidator();

  @Test
  void validateShouldReturnNoErrorsForAWellFormedGraph() {
    WorkflowGraph graph = WorkflowGraph.parse("""
      {
        "nodes": [
          {"id": "t1", "type": "TRIGGER"},
          {"id": "g1", "type": "GATEWAY"},
          {"id": "n1", "type": "NOTIFICATION"},
          {"id": "e1", "type": "END"}
        ],
        "edges": [
          {"id": "e-1", "source": "t1", "target": "g1"},
          {"id": "e-2", "source": "g1", "target": "n1", "sourceHandle": "yes"},
          {"id": "e-3", "source": "g1", "target": "e1", "sourceHandle": "no"},
          {"id": "e-4", "source": "n1", "target": "e1"}
        ]
      }
      """);

    List<String> errors = validator.validate(graph);

    assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
  }

  @Test
  void validateShouldRejectAGraphWithNoNodesAtAll() {
    WorkflowGraph graph = WorkflowGraph.parse("{\"nodes\":[],\"edges\":[]}");

    List<String> errors = validator.validate(graph);

    assertEquals(List.of("The workflow graph has no nodes"), errors);
  }

  @Test
  void validateShouldRejectAGraphWithNoTriggerNode() {
    WorkflowGraph graph = WorkflowGraph.parse("{\"nodes\":[{\"id\":\"e1\",\"type\":\"END\"}],\"edges\":[]}");

    List<String> errors = validator.validate(graph);

    assertTrue(errors.stream().anyMatch(e -> e.contains("no TRIGGER node")));
  }

  @Test
  void validateShouldRejectAGraphWithMoreThanOneTriggerNode() {
    WorkflowGraph graph = WorkflowGraph.parse("""
      {
        "nodes": [
          {"id": "t1", "type": "TRIGGER"},
          {"id": "t2", "type": "TRIGGER"}
        ],
        "edges": []
      }
      """);

    List<String> errors = validator.validate(graph);

    assertTrue(errors.stream().anyMatch(e -> e.contains("more than one TRIGGER node")));
  }

  @Test
  void validateShouldRejectDuplicateNodeIds() {
    WorkflowGraph graph = WorkflowGraph.parse("""
      {
        "nodes": [
          {"id": "t1", "type": "TRIGGER"},
          {"id": "t1", "type": "END"}
        ],
        "edges": []
      }
      """);

    List<String> errors = validator.validate(graph);

    assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate node id: t1")));
  }

  @Test
  void validateShouldRejectANodeWithAnUnknownType() {
    WorkflowGraph graph = WorkflowGraph.parse("{\"nodes\":[{\"id\":\"x1\",\"type\":\"MYSTERY\"}],\"edges\":[]}");

    List<String> errors = validator.validate(graph);

    assertTrue(errors.stream().anyMatch(e -> e.contains("unknown type")));
  }

  @Test
  void validateShouldRejectAnEdgeWithADanglingSourceOrTarget() {
    WorkflowGraph graph = WorkflowGraph.parse("""
      {
        "nodes": [
          {"id": "t1", "type": "TRIGGER"},
          {"id": "e1", "type": "END"}
        ],
        "edges": [
          {"id": "bad-1", "source": "t1", "target": "ghost"},
          {"id": "bad-2", "source": "ghost2", "target": "e1"}
        ]
      }
      """);

    List<String> errors = validator.validate(graph);

    assertTrue(errors.stream().anyMatch(e -> e.contains("dangling target: ghost")));
    assertTrue(errors.stream().anyMatch(e -> e.contains("dangling source: ghost2")));
  }

  @Test
  void validateShouldRequireAGatewayToHaveExactlyOneYesAndOneNoEdge() {
    WorkflowGraph graph = WorkflowGraph.parse("""
      {
        "nodes": [
          {"id": "t1", "type": "TRIGGER"},
          {"id": "g1", "type": "GATEWAY"},
          {"id": "e1", "type": "END"}
        ],
        "edges": [
          {"id": "e-1", "source": "t1", "target": "g1"},
          {"id": "e-2", "source": "g1", "target": "e1", "sourceHandle": "yes"}
        ]
      }
      """);

    List<String> errors = validator.validate(graph);

    assertTrue(errors.stream().anyMatch(e -> e.contains("must have exactly one 'yes' and one 'no'")));
  }

  @Test
  void validateShouldRejectAnEndNodeWithAnOutgoingEdge() {
    WorkflowGraph graph = WorkflowGraph.parse("""
      {
        "nodes": [
          {"id": "t1", "type": "TRIGGER"},
          {"id": "e1", "type": "END"}
        ],
        "edges": [
          {"id": "e-1", "source": "t1", "target": "e1"},
          {"id": "e-2", "source": "e1", "target": "t1"}
        ]
      }
      """);

    List<String> errors = validator.validate(graph);

    assertTrue(errors.stream().anyMatch(e -> e.contains("must not have outgoing edges")));
  }

  @Test
  void validateShouldRequireANonGatewayNonEndNodeToHaveExactlyOneOutgoingEdge() {
    WorkflowGraph graph = WorkflowGraph.parse("""
      {
        "nodes": [
          {"id": "t1", "type": "TRIGGER"},
          {"id": "n1", "type": "NOTIFICATION"},
          {"id": "e1", "type": "END"},
          {"id": "e2", "type": "END"}
        ],
        "edges": [
          {"id": "e-1", "source": "t1", "target": "n1"},
          {"id": "e-2", "source": "n1", "target": "e1"},
          {"id": "e-3", "source": "n1", "target": "e2"}
        ]
      }
      """);

    List<String> errors = validator.validate(graph);

    assertTrue(errors.stream().anyMatch(e -> e.contains("must have exactly one outgoing edge")));
  }

  @Test
  void validateShouldReportNodesThatAreNotReachableFromTheTrigger() {
    WorkflowGraph graph = WorkflowGraph.parse("""
      {
        "nodes": [
          {"id": "t1", "type": "TRIGGER"},
          {"id": "e1", "type": "END"},
          {"id": "orphan", "type": "END"}
        ],
        "edges": [
          {"id": "e-1", "source": "t1", "target": "e1"}
        ]
      }
      """);

    List<String> errors = validator.validate(graph);

    assertTrue(errors.stream().anyMatch(e -> e.contains("Node [orphan] is not reachable")));
  }

  @Test
  void validateShouldSkipReachabilityCheckWhenStructuralErrorsAlreadyExist() {
    // No TRIGGER node at all — reachability can't even be computed, and the
    // validator's own contract is "only run checkReachability when errors is
    // still empty at that point", so this exercises that short-circuit.
    WorkflowGraph graph = WorkflowGraph.parse("{\"nodes\":[{\"id\":\"e1\",\"type\":\"END\"}],\"edges\":[]}");

    List<String> errors = validator.validate(graph);

    assertTrue(errors.stream().noneMatch(e -> e.contains("not reachable")));
  }
}
