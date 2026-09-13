package com.eventflow.ingestion.workflow.engine;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.workflow.dto.BusinessEvent;
import com.eventflow.ingestion.workflow.model.Workflow;
import com.eventflow.ingestion.workflow.model.WorkflowStatus;
import com.eventflow.ingestion.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Entry point of the engine: listens for business events on
 * notification.events and spawns one WorkflowExecution per ACTIVE
 * workflow whose triggerEventType matches — fan-out is intentional, since
 * activation already rejects a second ACTIVE workflow on the same event
 * type (WorkflowService#activate), so in practice this is 0 or 1 matches
 * today, with room for real fan-out later without an engine change.
 *
 * Deliberately does the absolute minimum per event: find the matching
 * workflow(s) and delegate creation to WorkflowExecutionEngine#spawn (a
 * separate bean, so its @Transactional actually applies — see that class's
 * javadoc). This keeps the trigger path cheap and unable to get stuck
 * mid-workflow if the process crashes right after committing; all the
 * actual node-by-node work happens via WorkflowAdvanceConsumer instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTriggerConsumer {

  private final WorkflowRepository workflowRepository;
  private final WorkflowExecutionEngine workflowExecutionEngine;

  @KafkaListener(topics = KafkaTopicConfig.TOPIC_BUSINESS_EVENTS, groupId = "workflow-trigger-group")
  public void consume(BusinessEvent event) {
    if (event.getEventType() == null || event.getEventType().isBlank()) {
      log.warn("Received a business event with no eventType — ignoring: {}", event);
      return;
    }

    List<Workflow> matches = workflowRepository.findByStatusAndTriggerEventType(WorkflowStatus.ACTIVE, event.getEventType());
    if (matches.isEmpty()) {
      log.info("Business event [{}] matched no ACTIVE workflow — no execution spawned", event.getEventType());
      return;
    }

    for (Workflow workflow : matches) {
      workflowExecutionEngine.spawn(workflow, event);
    }
  }
}
