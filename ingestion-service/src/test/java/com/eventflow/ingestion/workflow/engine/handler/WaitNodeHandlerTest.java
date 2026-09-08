package com.eventflow.ingestion.workflow.engine.handler;

import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WaitNodeHandler computes wakeAt from LocalDateTime.now() internally, so
 * these tests bracket the call with a "before"/"after" timestamp and assert
 * wakeAt falls within that window plus the configured duration, rather than
 * asserting an exact equality that real execution time would flake on.
 */
class WaitNodeHandlerTest {

  private final WaitNodeHandler handler = new WaitNodeHandler();
  private final WorkflowExecution execution = WorkflowExecution.builder().id(1L).build();

  @Test
  void nodeTypeShouldBeWait() {
    assertEquals("WAIT", handler.nodeType());
  }

  @Test
  void handleShouldDefaultToOneHourWhenNoConfigIsGiven() {
    NodeDef node = NodeDef.builder().id("w1").type("WAIT").config(Map.of()).build();

    LocalDateTime before = LocalDateTime.now();
    NodeOutcome outcome = handler.handle(execution, node, Map.of());
    LocalDateTime after = LocalDateTime.now();

    assertEquals(NodeOutcome.Type.SUSPEND, outcome.getType());
    assertFalse(outcome.getWakeAt().isBefore(before.plusHours(1)));
    assertFalse(outcome.getWakeAt().isAfter(after.plusHours(1)));
    assertEquals("Attente programmée : 1 hours", outcome.getLogMessage());
  }

  @Test
  void handleShouldHonorACustomDurationAndUnit() {
    NodeDef node = NodeDef.builder().id("w1").type("WAIT")
      .config(Map.of("duration", 30, "unit", "minutes")).build();

    LocalDateTime before = LocalDateTime.now();
    NodeOutcome outcome = handler.handle(execution, node, Map.of());
    LocalDateTime after = LocalDateTime.now();

    assertFalse(outcome.getWakeAt().isBefore(before.plusMinutes(30)));
    assertFalse(outcome.getWakeAt().isAfter(after.plusMinutes(30)));
    assertEquals("Attente programmée : 30 minutes", outcome.getLogMessage());
  }

  @Test
  void handleShouldUppercaseALowercaseUnitNameBeforeResolvingIt() {
    NodeDef node = NodeDef.builder().id("w1").type("WAIT")
      .config(Map.of("duration", 2, "unit", "days")).build();

    NodeOutcome outcome = handler.handle(execution, node, Map.of());

    assertEquals(NodeOutcome.Type.SUSPEND, outcome.getType());
    assertTrue(outcome.getWakeAt().isAfter(LocalDateTime.now().plusHours(23)));
  }

  @Test
  void handleShouldFailForAnInvalidChronoUnitName() {
    NodeDef node = NodeDef.builder().id("w1").type("WAIT")
      .config(Map.of("duration", 5, "unit", "FORTNIGHTS")).build();

    NodeOutcome outcome = handler.handle(execution, node, Map.of());

    assertEquals(NodeOutcome.Type.FAIL, outcome.getType());
    assertEquals("WAIT node [w1] has an invalid unit: FORTNIGHTS", outcome.getErrorMessage());
  }
}
