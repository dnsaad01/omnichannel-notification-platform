package com.eventflow.ingestion.workflow.engine;

import com.eventflow.ingestion.workflow.dto.WorkflowAdvanceMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Thin pass-through consumer — WorkflowExecutionEngine#advance's own logic
 * is covered exhaustively by WorkflowExecutionEngineTest, so this only needs
 * to prove the guard clause and the delegation itself.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowAdvanceConsumerTest {

  @Mock
  private WorkflowExecutionEngine workflowExecutionEngine;

  private WorkflowAdvanceConsumer newConsumer() {
    return new WorkflowAdvanceConsumer(workflowExecutionEngine);
  }

  @Test
  void consumeShouldIgnoreAMessageWithNoExecutionId() {
    WorkflowAdvanceConsumer consumer = newConsumer();
    WorkflowAdvanceMessage message = WorkflowAdvanceMessage.builder().executionId(null).build();

    consumer.consume(message);

    verify(workflowExecutionEngine, never()).advance(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void consumeShouldDelegateToTheEngineWhenAnExecutionIdIsPresent() {
    WorkflowAdvanceConsumer consumer = newConsumer();
    WorkflowAdvanceMessage message = WorkflowAdvanceMessage.builder().executionId(77L).build();

    consumer.consume(message);

    verify(workflowExecutionEngine).advance(77L);
  }
}
