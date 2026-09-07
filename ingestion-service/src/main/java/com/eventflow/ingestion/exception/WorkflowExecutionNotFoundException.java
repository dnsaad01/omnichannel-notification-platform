package com.eventflow.ingestion.exception;

public class WorkflowExecutionNotFoundException extends RuntimeException {
  public WorkflowExecutionNotFoundException(Long id) {
    super("Workflow execution not found: " + id);
  }
}
