package com.eventflow.ingestion.workflow.engine.handler;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * What a NodeHandler decided, and what WorkflowExecutionEngine should do
 * about it. See NodeHandler's javadoc for why handlers never touch
 * WorkflowExecution's state directly.
 */
@Getter
@Builder
public class NodeOutcome {

  public enum Type {
    /** Keep looping in the same advance() call — move to this node's
     *  outgoing edge and dispatch the next node immediately. */
    CONTINUE,
    /** Suspend the execution (WAIT only) — engine sets status=WAITING,
     *  nextWakeAt=wakeAt, and commits. */
    SUSPEND,
    /** Terminal success (END only) — engine sets status=COMPLETED. */
    COMPLETE,
    /** Terminal failure — engine sets status=FAILED and logs errorMessage. */
    FAIL
  }

  private final Type type;

  /** GATEWAY only: "yes" or "no" — which outgoing edge the engine should
   *  follow next. Null for every other CONTINUE outcome (single edge). */
  private final String branch;

  /** SUSPEND only: when the wait-resume scheduler should pick this back up. */
  private final LocalDateTime wakeAt;

  /** Human-readable line appended to the execution's Timeline. */
  private final String logMessage;

  /** FAIL only. */
  private final String errorMessage;

  public static NodeOutcome continueTo(String logMessage) {
    return NodeOutcome.builder().type(Type.CONTINUE).logMessage(logMessage).build();
  }

  public static NodeOutcome continueBranch(String branch, String logMessage) {
    return NodeOutcome.builder().type(Type.CONTINUE).branch(branch).logMessage(logMessage).build();
  }

  public static NodeOutcome suspendUntil(LocalDateTime wakeAt, String logMessage) {
    return NodeOutcome.builder().type(Type.SUSPEND).wakeAt(wakeAt).logMessage(logMessage).build();
  }

  public static NodeOutcome complete(String logMessage) {
    return NodeOutcome.builder().type(Type.COMPLETE).logMessage(logMessage).build();
  }

  public static NodeOutcome fail(String errorMessage) {
    return NodeOutcome.builder().type(Type.FAIL).errorMessage(errorMessage).build();
  }
}
