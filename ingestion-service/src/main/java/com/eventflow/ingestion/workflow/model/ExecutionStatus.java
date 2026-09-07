package com.eventflow.ingestion.workflow.model;

/**
 * ADVANCING is a short-lived, deliberately-visible claimed state: the
 * wait-resume scheduler (Phase 1) flips WAITING -> ADVANCING atomically via
 * an UPDATE...WHERE status='WAITING' before publishing to the internal
 * advance topic, so two service instances can never both pick up the same
 * expired wait.
 */
public enum ExecutionStatus {
  RUNNING,
  WAITING,
  ADVANCING,
  COMPLETED,
  FAILED
}
