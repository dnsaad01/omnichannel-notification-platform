package com.eventflow.ingestion.workflow.engine.handler;

import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Executes an END node — always terminal, always COMPLETE. */
@Component
public class EndNodeHandler implements NodeHandler {

  @Override
  public String nodeType() {
    return "END";
  }

  @Override
  public NodeOutcome handle(WorkflowExecution execution, NodeDef node, Map<String, Object> context) {
    return NodeOutcome.complete("Workflow terminé");
  }
}
