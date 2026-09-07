package com.eventflow.ingestion.workflow.model;

/**
 * Lifecycle of a Workflow definition. Editing an ACTIVE workflow creates a
 * new DRAFT revision (same workflow "identity", version + 1) rather than
 * mutating the live graph in place — see WorkflowExecution.workflowVersion,
 * which pins each execution to the exact graph it started with.
 */
public enum WorkflowStatus {
  DRAFT,
  ACTIVE,
  DISABLED,
  ARCHIVED
}
