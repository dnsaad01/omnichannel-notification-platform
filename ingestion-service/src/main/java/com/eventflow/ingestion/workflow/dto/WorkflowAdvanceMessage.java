package com.eventflow.ingestion.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload on KafkaTopicConfig.TOPIC_WORKFLOW_ADVANCE — "this execution needs
 * to move forward." Published by WorkflowTriggerConsumer (fresh match) and
 * WorkflowWaitScheduler (a wait timer expired). Deliberately just an id: all
 * state lives in the workflow_executions row, so redelivery is naturally
 * idempotent (WorkflowExecutionEngine re-reads the row and no-ops if it's
 * not RUNNING/ADVANCING anymore).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowAdvanceMessage {
  private Long executionId;
}
