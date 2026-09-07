package com.eventflow.ingestion.exception;

public class WorkflowNotFoundException extends RuntimeException {
  public WorkflowNotFoundException(Long id) {
    super("Workflow not found: " + id);
  }
}
