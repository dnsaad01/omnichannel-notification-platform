package com.eventflow.ingestion.workflow.engine;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.workflow.dto.WorkflowAdvanceMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka listener for the internal control loop — everything real
 * happens in WorkflowExecutionEngine#advance, which is deliberately kept
 * Kafka-agnostic and easy to unit test on its own. Fed by both
 * WorkflowTriggerConsumer (a fresh match) and WorkflowWaitScheduler (a wait
 * timer expiring) — see architecture plan §2.4 for why both go through one
 * consumer instead of one of them calling the engine directly in-process.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowAdvanceConsumer {

  private final WorkflowExecutionEngine workflowExecutionEngine;

  @KafkaListener(topics = KafkaTopicConfig.TOPIC_WORKFLOW_ADVANCE, groupId = "workflow-advance-group")
  public void consume(WorkflowAdvanceMessage message) {
    if (message.getExecutionId() == null) {
      log.warn("Received a workflow advance message with no executionId — ignoring: {}", message);
      return;
    }
    workflowExecutionEngine.advance(message.getExecutionId());
  }
}
