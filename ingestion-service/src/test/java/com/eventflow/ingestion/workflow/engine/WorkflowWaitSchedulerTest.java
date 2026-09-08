package com.eventflow.ingestion.workflow.engine;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.workflow.dto.WorkflowAdvanceMessage;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowWaitSchedulerTest {

  @Mock
  private WorkflowExecutionRepository workflowExecutionRepository;

  @Mock
  private KafkaTemplate<String, WorkflowAdvanceMessage> advanceKafkaTemplate;

  private WorkflowWaitScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new WorkflowWaitScheduler(workflowExecutionRepository, advanceKafkaTemplate);
  }

  @Test
  void resumeDueExecutionsShouldSendNothingWhenNoExecutionsAreDue() {
    when(workflowExecutionRepository.claimDueWaitingExecutions(any())).thenReturn(List.of());

    scheduler.resumeDueExecutions();

    verify(advanceKafkaTemplate, never()).send(any(), any(), any());
  }

  @Test
  void resumeDueExecutionsShouldPublishOneAdvanceMessagePerClaimedId() {
    when(workflowExecutionRepository.claimDueWaitingExecutions(any())).thenReturn(List.of(11L, 22L));

    scheduler.resumeDueExecutions();

    ArgumentCaptor<WorkflowAdvanceMessage> firstCaptor = ArgumentCaptor.forClass(WorkflowAdvanceMessage.class);
    ArgumentCaptor<WorkflowAdvanceMessage> secondCaptor = ArgumentCaptor.forClass(WorkflowAdvanceMessage.class);
    verify(advanceKafkaTemplate).send(org.mockito.ArgumentMatchers.eq(KafkaTopicConfig.TOPIC_WORKFLOW_ADVANCE),
      org.mockito.ArgumentMatchers.eq("11"), firstCaptor.capture());
    verify(advanceKafkaTemplate).send(org.mockito.ArgumentMatchers.eq(KafkaTopicConfig.TOPIC_WORKFLOW_ADVANCE),
      org.mockito.ArgumentMatchers.eq("22"), secondCaptor.capture());
    assertEquals(11L, firstCaptor.getValue().getExecutionId());
    assertEquals(22L, secondCaptor.getValue().getExecutionId());
    verify(advanceKafkaTemplate, times(2)).send(any(), any(), any());
  }

  @Test
  void resumeDueExecutionsShouldQueryUsingTheCurrentTime() {
    when(workflowExecutionRepository.claimDueWaitingExecutions(any())).thenReturn(List.of());
    LocalDateTime before = LocalDateTime.now();

    scheduler.resumeDueExecutions();

    ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(workflowExecutionRepository).claimDueWaitingExecutions(nowCaptor.capture());
    LocalDateTime after = LocalDateTime.now();
    assertTrue(!nowCaptor.getValue().isBefore(before) && !nowCaptor.getValue().isAfter(after));
  }
}
