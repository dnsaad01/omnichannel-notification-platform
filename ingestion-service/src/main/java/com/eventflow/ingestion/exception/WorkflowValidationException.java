package com.eventflow.ingestion.exception;

import java.util.List;

/**
 * Thrown by WorkflowGraphValidator when POST /api/workflows/{id}/activate is
 * called on a graph that fails structural validation (missing/duplicate
 * TRIGGER, unreachable node, a GATEWAY without both yes/no edges, a dangling
 * edge, or a trigger_event_type already claimed by another ACTIVE workflow).
 * Carries every violation found in one pass rather than failing on the
 * first, so the caller doesn't have to fix-and-retry one error at a time.
 */
public class WorkflowValidationException extends RuntimeException {

  private final List<String> errors;

  public WorkflowValidationException(List<String> errors) {
    super("Workflow validation failed: " + String.join("; ", errors));
    this.errors = errors;
  }

  public List<String> getErrors() {
    return errors;
  }
}
