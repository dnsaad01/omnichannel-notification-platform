package com.eventflow.ingestion.workflow.engine;

import com.eventflow.ingestion.workflow.dto.BusinessEvent;
import com.eventflow.ingestion.workflow.model.Workflow;
import com.eventflow.ingestion.workflow.model.WorkflowStatus;
import com.eventflow.ingestion.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTriggerConsumerTest {

  @Mock
  private WorkflowRepository workflowRepository;

  @Mock
  private WorkflowExecutionEngine workflowExecutionEngine;

  private WorkflowTriggerConsumer consumer;

  private WorkflowTriggerConsumer newConsumer() {
    return new WorkflowTriggerConsumer(workflowRepository, workflowExecutionEngine);
  }

  private Workflow activeWorkflow(Long id) {
    return Workflow.builder().id(id).name("WF-" + id).status(WorkflowStatus.ACTIVE).triggerEventType("CART_ABANDONED").build();
  }

  @Test
  void consumeShouldIgnoreAnEventWithABlankEventType() {
    consumer = newConsumer();
    BusinessEvent event = BusinessEvent.builder().eventType("  ").build();

    consumer.consume(event);

    verify(workflowRepository, never()).findByStatusAndTriggerEventType(any(), any());
    verify(workflowExecutionEngine, never()).spawn(any(), any());
  }

  @Test
  void consumeShouldIgnoreAnEventWithANullEventType() {
    consumer = newConsumer();
    BusinessEvent event = BusinessEvent.builder().eventType(null).build();

    consumer.consume(event);

    verify(workflowRepository, never()).findByStatusAndTriggerEventType(any(), any());
    verify(workflowExecutionEngine, never()).spawn(any(), any());
  }

  @Test
  void consumeShouldDoNothingWhenNoActiveWorkflowMatchesTheEventType() {
    consumer = newConsumer();
    BusinessEvent event = BusinessEvent.builder().eventType("CART_ABANDONED").build();
    when(workflowRepository.findByStatusAndTriggerEventType(WorkflowStatus.ACTIVE, "CART_ABANDONED")).thenReturn(List.of());

    consumer.consume(event);

    verify(workflowExecutionEngine, never()).spawn(any(), any());
  }

  @Test
  void consumeShouldSpawnOneExecutionForASingleMatchingWorkflow() {
    consumer = newConsumer();
    BusinessEvent event = BusinessEvent.builder().eventType("CART_ABANDONED").build();
    Workflow workflow = activeWorkflow(1L);
    when(workflowRepository.findByStatusAndTriggerEventType(WorkflowStatus.ACTIVE, "CART_ABANDONED")).thenReturn(List.of(workflow));

    consumer.consume(event);

    verify(workflowExecutionEngine, times(1)).spawn(eq(workflow), eq(event));
  }

  @Test
  void consumeShouldSpawnOneExecutionPerMatchWhenMultipleWorkflowsShareTheEventType() {
    consumer = newConsumer();
    BusinessEvent event = BusinessEvent.builder().eventType("CART_ABANDONED").build();
    Workflow first = activeWorkflow(1L);
    Workflow second = activeWorkflow(2L);
    when(workflowRepository.findByStatusAndTriggerEventType(WorkflowStatus.ACTIVE, "CART_ABANDONED"))
      .thenReturn(List.of(first, second));

    consumer.consume(event);

    verify(workflowExecutionEngine).spawn(first, event);
    verify(workflowExecutionEngine).spawn(second, event);
    verify(workflowExecutionEngine, times(2)).spawn(any(), any());
  }
}
