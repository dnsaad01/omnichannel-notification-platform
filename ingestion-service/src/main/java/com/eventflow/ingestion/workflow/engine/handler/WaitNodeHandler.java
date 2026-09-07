package com.eventflow.ingestion.workflow.engine.handler;

import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Executes a WAIT node — the one suspend point in the engine (plan §5).
 * Computes nextWakeAt and returns SUSPEND; WorkflowWaitScheduler is what
 * later notices nextWakeAt has passed and re-publishes onto the advance
 * topic, at which point the engine re-enters at this same node and resumes
 * from its single outgoing edge.
 *
 * Expected node config: { "duration": 2, "unit": "HOURS" }
 * unit is any java.time.temporal.ChronoUnit name (MINUTES, HOURS, DAYS, ...).
 */
@Component
public class WaitNodeHandler implements NodeHandler {

  @Override
  public String nodeType() {
    return "WAIT";
  }

  @Override
  public NodeOutcome handle(WorkflowExecution execution, NodeDef node, Map<String, Object> context) {
    Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
    long duration = config.get("duration") != null ? ((Number) config.get("duration")).longValue() : 1L;
    String unitName = config.get("unit") != null ? String.valueOf(config.get("unit")).toUpperCase() : "HOURS";

    ChronoUnit unit;
    try {
      unit = ChronoUnit.valueOf(unitName);
    } catch (IllegalArgumentException e) {
      return NodeOutcome.fail("WAIT node [" + node.getId() + "] has an invalid unit: " + unitName);
    }

    LocalDateTime wakeAt = LocalDateTime.now().plus(duration, unit);
    return NodeOutcome.suspendUntil(wakeAt, "Attente programmée : " + duration + " " + unitName.toLowerCase());
  }
}
