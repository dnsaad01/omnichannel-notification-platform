package com.eventflow.ingestion.workflow.engine.handler;

import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;

import java.util.Map;

/**
 * Strategy for one node type (NOTIFICATION / WAIT / GATEWAY / END).
 * TRIGGER has no handler — it's only ever the starting point of
 * execution.currentNodeId, never a node the engine "arrives at" and dispatches.
 *
 * A handler never mutates execution.status/currentNodeId/nextWakeAt itself —
 * it only returns a NodeOutcome describing what happened, and
 * WorkflowExecutionEngine is the single place that turns an outcome into
 * persisted state. That keeps "what CONTINUE/SUSPEND/COMPLETE/FAIL actually
 * do to the row" from drifting between handler implementations.
 *
 * Implementations are picked up automatically: WorkflowExecutionEngine
 * collects every NodeHandler bean into a Map keyed by nodeType(), so adding
 * a new node type later is just a new @Component, no engine change.
 */
public interface NodeHandler {

  /** The NodeDef.type this handler executes, e.g. "NOTIFICATION". */
  String nodeType();

  /**
   * @param execution read-only here (id, workflowId, etc.) — handlers must
   *                  not call save(); the engine persists once per advance().
   * @param node      the node being executed, already resolved from the graph.
   * @param context   the execution's context map, mutable — handlers write
   *                  results into it (e.g. "email.sent" = true) so later
   *                  nodes (a GATEWAY checking "email.opened", say) can see them.
   */
  NodeOutcome handle(WorkflowExecution execution, NodeDef node, Map<String, Object> context);
}
