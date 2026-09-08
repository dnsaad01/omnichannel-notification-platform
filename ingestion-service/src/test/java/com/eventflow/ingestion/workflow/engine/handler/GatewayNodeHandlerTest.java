package com.eventflow.ingestion.workflow.engine.handler;

import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GatewayNodeHandler is pure, stateless logic over a Map context — no
 * collaborators to mock, so this is a plain (non-Mockito) unit test that
 * exercises every operator branch directly.
 */
class GatewayNodeHandlerTest {

  private final GatewayNodeHandler handler = new GatewayNodeHandler();
  private final WorkflowExecution execution = WorkflowExecution.builder().id(1L).build();

  private NodeDef gatewayNode(String variable, String operator, Object value) {
    Map<String, Object> config = new HashMap<>();
    config.put("variable", variable);
    if (operator != null) {
      config.put("operator", operator);
    }
    config.put("value", value);
    return NodeDef.builder().id("g1").type("GATEWAY").config(config).build();
  }

  @Test
  void nodeTypeShouldBeGateway() {
    assertEquals("GATEWAY", handler.nodeType());
  }

  @Test
  void existsShouldBranchYesWhenTheVariableIsPresent() {
    NodeDef node = gatewayNode("user.email", "exists", null);
    Map<String, Object> context = Map.of("user", Map.of("email", "a@example.com"));

    NodeOutcome outcome = handler.handle(execution, node, context);

    assertEquals(NodeOutcome.Type.CONTINUE, outcome.getType());
    assertEquals("yes", outcome.getBranch());
  }

  @Test
  void existsShouldBranchNoWhenTheVariableIsAbsent() {
    NodeDef node = gatewayNode("user.email", "exists", null);

    NodeOutcome outcome = handler.handle(execution, node, Map.of());

    assertEquals("no", outcome.getBranch());
  }

  @Test
  void equalsShouldBranchYesForMatchingStrings() {
    NodeDef node = gatewayNode("status", "equals", "ACTIVE");
    Map<String, Object> context = Map.of("status", "ACTIVE");

    assertEquals("yes", handler.handle(execution, node, context).getBranch());
  }

  @Test
  void equalsShouldLooselyCompareBooleansAgainstStringValues() {
    NodeDef node = gatewayNode("email.opened", "equals", "true");
    Map<String, Object> context = Map.of("email", Map.of("opened", Boolean.TRUE));

    assertEquals("yes", handler.handle(execution, node, context).getBranch());
  }

  @Test
  void equalsShouldLooselyCompareNumbersRegardlessOfType() {
    NodeDef node = gatewayNode("cartValue", "equals", 89);
    Map<String, Object> context = Map.of("cartValue", 89.0);

    assertEquals("yes", handler.handle(execution, node, context).getBranch());
  }

  @Test
  void notEqualsShouldBranchYesWhenValuesDiffer() {
    NodeDef node = gatewayNode("status", "notEquals", "ACTIVE");
    Map<String, Object> context = Map.of("status", "CANCELLED");

    assertEquals("yes", handler.handle(execution, node, context).getBranch());
  }

  @Test
  void greaterThanShouldBranchYesWhenTheActualNumberIsLarger() {
    NodeDef node = gatewayNode("cartValue", "greaterThan", 50);
    Map<String, Object> context = Map.of("cartValue", 89.9);

    assertEquals("yes", handler.handle(execution, node, context).getBranch());
  }

  @Test
  void greaterThanShouldBranchNoWhenEitherSideIsNotNumeric() {
    NodeDef node = gatewayNode("cartValue", "greaterThan", 50);
    Map<String, Object> context = Map.of("cartValue", "not-a-number");

    assertEquals("no", handler.handle(execution, node, context).getBranch());
  }

  @Test
  void lessThanShouldBranchYesWhenTheActualNumberIsSmaller() {
    NodeDef node = gatewayNode("cartValue", "lessThan", 100);
    Map<String, Object> context = Map.of("cartValue", 10);

    assertEquals("yes", handler.handle(execution, node, context).getBranch());
  }

  @Test
  void handleShouldFailWhenNoVariableIsConfigured() {
    NodeDef node = NodeDef.builder().id("g1").type("GATEWAY").config(Map.of("operator", "equals")).build();

    NodeOutcome outcome = handler.handle(execution, node, Map.of());

    assertEquals(NodeOutcome.Type.FAIL, outcome.getType());
    assertEquals("GATEWAY node [g1] has no variable configured", outcome.getErrorMessage());
  }

  @Test
  void handleShouldThrowForAnUnsupportedOperator() {
    NodeDef node = gatewayNode("status", "startsWith", "A");

    assertThrows(IllegalStateException.class, () -> handler.handle(execution, node, Map.of("status", "ACTIVE")));
  }
}
