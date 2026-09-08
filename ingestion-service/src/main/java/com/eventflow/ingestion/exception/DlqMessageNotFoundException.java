package com.eventflow.ingestion.exception;

public class DlqMessageNotFoundException extends RuntimeException {
  public DlqMessageNotFoundException(Long id) {
    super("DLQ message not found: " + id);
  }
}
