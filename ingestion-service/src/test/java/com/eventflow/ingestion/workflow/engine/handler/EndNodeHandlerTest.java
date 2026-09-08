package com.eventflow.ingestion.workflow.engine.handler;

import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndNodeHandlerTest {

  private final EndNodeHandler handler = new EndNodeHandler();

  @Test
  void nodeTypeShouldBeEnd() {
    assertEquals("END", handler.nodeType());
  }

  @Test
  void handleShouldAlwaysReturnAnUnconditionalCompleteOutcome() {
    WorkflowExecution execution = WorkflowExecution.builder().id(1L).build();
    NodeDef node = NodeDef.builder().id("e1").type("END").build();

    NodeOutcome outcome = handler.handle(execution, node, Map.of());

    assertEquals(NodeOutcome.Type.COMPLETE, outcome.getType());
    assertEquals("Workflow terminé", outcome.getLogMessage());
  }
}
