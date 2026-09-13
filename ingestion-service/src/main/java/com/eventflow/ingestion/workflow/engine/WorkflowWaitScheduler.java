package com.eventflow.ingestion.workflow.engine;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.workflow.dto.WorkflowAdvanceMessage;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wait-resume scheduler. Every 15s, atomically claims every WAITING
 * execution whose nextWakeAt has passed — via
 * WorkflowExecutionRepository#claimDueWaitingExecutions's native
 * UPDATE...WHERE status='WAITING'...RETURNING id — and republishes each
 * claimed id onto the advance topic.
 *
 * The claim is the concurrency-safety mechanism: Postgres serializes the
 * UPDATE, so if this ever runs on more than one ingestion-service instance,
 * two instances can never both claim the same row. This gets production-safe
 * horizontal scaling without adding Quartz.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowWaitScheduler {

  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final KafkaTemplate<String, WorkflowAdvanceMessage> advanceKafkaTemplate;

  @Scheduled(fixedDelay = 15000)
  @Transactional
  public void resumeDueExecutions() {
    List<Long> claimedIds = workflowExecutionRepository.claimDueWaitingExecutions(LocalDateTime.now());
    if (claimedIds.isEmpty()) {
      return;
    }

    log.info("Wait-resume scheduler claimed {} due execution(s): {}", claimedIds.size(), claimedIds);
    for (Long executionId : claimedIds) {
      advanceKafkaTemplate.send(KafkaTopicConfig.TOPIC_WORKFLOW_ADVANCE, String.valueOf(executionId),
        WorkflowAdvanceMessage.builder().executionId(executionId).build());
    }
  }
}
