package com.eventflow.ingestion.workflow.engine;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.workflow.dto.BusinessEvent;
import com.eventflow.ingestion.workflow.dto.WorkflowAdvanceMessage;
import com.eventflow.ingestion.workflow.engine.handler.NodeHandler;
import com.eventflow.ingestion.workflow.engine.handler.NodeOutcome;
import com.eventflow.ingestion.workflow.model.ExecutionStatus;
import com.eventflow.ingestion.workflow.model.Workflow;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionRepository;
import com.eventflow.ingestion.workflow.repository.WorkflowRepository;
import com.eventflow.ingestion.workflow.service.WorkflowExecutionLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers WorkflowExecutionEngine's two public entry points against real
 * WorkflowGraph JSON (same text-block-graph convention as WorkflowServiceTest)
 * with NodeHandler collaborators mocked per test rather than reusing the real
 * handler implementations — that keeps this class focused purely on the
 * engine's own control flow (the loop, the step cap, the CONTINUE/SUSPEND/
 * COMPLETE/FAIL switch, persistence) independently of any one handler's
 * business logic, which is covered by its own dedicated test class instead.
 *
 * @PostConstruct never fires outside a real Spring context, so every test
 * builds the engine via {@link #buildEngine(NodeHandler...)}, which calls the
 * package-private indexHandlers() manually right after construction.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowExecutionEngineTest {

  private static final String SIMPLE_TRIGGER_END_GRAPH = """
    {
      "nodes": [
        {"id": "t1", "type": "TRIGGER"},
        {"id": "e1", "type": "END"}
      ],
      "edges": [
        {"id": "edge1", "source": "t1", "target": "e1"}
      ]
    }
    """;

  private static final String GRAPH_TRIGGER_NOTIFICATION_WAIT = """
    {
      "nodes": [
        {"id": "t1", "type": "TRIGGER"},
        {"id": "n1", "type": "NOTIFICATION"},
        {"id": "w1", "type": "WAIT"}
      ],
      "edges": [
        {"id": "e1", "source": "t1", "target": "n1"},
        {"id": "e2", "source": "n1", "target": "w1"}
      ]
    }
    """;

  private static final String GRAPH_TRIGGER_GATEWAY_END = """
    {
      "nodes": [
        {"id": "t1", "type": "TRIGGER"},
        {"id": "g1", "type": "GATEWAY"},
        {"id": "e1", "type": "END"}
      ],
      "edges": [
        {"id": "edge1", "source": "t1", "target": "g1"},
        {"id": "edge2", "source": "g1", "target": "e1", "sourceHandle": "yes"}
      ]
    }
    """;

  private static final String GRAPH_TRIGGER_TO_GATEWAY = """
    {
      "nodes": [
        {"id": "t1", "type": "TRIGGER"},
        {"id": "g1", "type": "GATEWAY"}
      ],
      "edges": [
        {"id": "e1", "source": "t1", "target": "g1"}
      ]
    }
    """;

  private static final String GRAPH_DANGLING_TARGET = """
    {
      "nodes": [
        {"id": "t1", "type": "TRIGGER"}
      ],
      "edges": [
        {"id": "e1", "source": "t1", "target": "ghost"}
      ]
    }
    """;

  private static final String GRAPH_UNKNOWN_NODE_TYPE = """
    {
      "nodes": [
        {"id": "t1", "type": "TRIGGER"},
        {"id": "x1", "type": "MYSTERY"}
      ],
      "edges": [
        {"id": "e1", "source": "t1", "target": "x1"}
      ]
    }
    """;

  private static final String GRAPH_CYCLE = """
    {
      "nodes": [
        {"id": "t1", "type": "TRIGGER"},
        {"id": "a", "type": "NOTIFICATION"},
        {"id": "b", "type": "NOTIFICATION"}
      ],
      "edges": [
        {"id": "e1", "source": "t1", "target": "a"},
        {"id": "e2", "source": "a", "target": "b"},
        {"id": "e3", "source": "b", "target": "a"}
      ]
    }
    """;

  @Mock
  private WorkflowExecutionRepository workflowExecutionRepository;

  @Mock
  private WorkflowRepository workflowRepository;

  @Mock
  private WorkflowExecutionLogService executionLogService;

  @Mock
  private org.springframework.kafka.core.KafkaTemplate<String, WorkflowAdvanceMessage> advanceKafkaTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private WorkflowExecutionEngine buildEngine(NodeHandler... handlers) {
    WorkflowExecutionEngine engine = new WorkflowExecutionEngine(
      workflowExecutionRepository, workflowRepository, executionLogService, objectMapper,
      List.of(handlers), advanceKafkaTemplate);
    engine.indexHandlers();
    return engine;
  }

  private NodeHandler mockHandler(String type, NodeOutcome outcome) {
    NodeHandler handler = mock(NodeHandler.class);
    when(handler.nodeType()).thenReturn(type);
    when(handler.handle(any(), any(), any())).thenReturn(outcome);
    return handler;
  }

  private Workflow workflowWithGraph(String graphJson) {
    return Workflow.builder().id(10L).name("Test WF").version(1).definitionJson(graphJson).build();
  }

  private WorkflowExecution executionAt(String nodeId, ExecutionStatus status) {
    return WorkflowExecution.builder()
      .id(100L).workflowId(10L).workflowVersion(1)
      .status(status).currentNodeId(nodeId).contextJson("{}")
      .build();
  }

  // ---------------------------------------------------------------- spawn()

  @Test
  void spawnShouldCreateARunningExecutionAtTheTriggerNodeAndPublishAnAdvanceMessage() {
    WorkflowExecutionEngine engine = buildEngine();
    Workflow workflow = workflowWithGraph(SIMPLE_TRIGGER_END_GRAPH);
    when(workflowExecutionRepository.save(any())).thenAnswer(inv -> {
      WorkflowExecution exec = inv.getArgument(0);
      exec.setId(55L);
      return exec;
    });
    BusinessEvent event = BusinessEvent.builder().eventType("CART_ABANDONED").payload(Map.of("userId", "u1")).build();

    engine.spawn(workflow, event);

    ArgumentCaptor<WorkflowExecution> captor = ArgumentCaptor.forClass(WorkflowExecution.class);
    verify(workflowExecutionRepository).save(captor.capture());
    WorkflowExecution saved = captor.getValue();
    assertEquals(ExecutionStatus.RUNNING, saved.getStatus());
    assertEquals("t1", saved.getCurrentNodeId());
    assertEquals(10L, saved.getWorkflowId());
    assertTrue(saved.getContextJson().contains("u1"));

    verify(executionLogService, times(2)).info(eq(55L), any(), any(), any());

    ArgumentCaptor<WorkflowAdvanceMessage> messageCaptor = ArgumentCaptor.forClass(WorkflowAdvanceMessage.class);
    verify(advanceKafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_WORKFLOW_ADVANCE), eq("55"), messageCaptor.capture());
    assertEquals(55L, messageCaptor.getValue().getExecutionId());
  }

  @Test
  void spawnShouldDoNothingWhenTheGraphHasNoTriggerNode() {
    WorkflowExecutionEngine engine = buildEngine();
    Workflow workflow = workflowWithGraph("{\"nodes\":[{\"id\":\"e1\",\"type\":\"END\"}],\"edges\":[]}");
    BusinessEvent event = BusinessEvent.builder().eventType("X").payload(Map.of()).build();

    engine.spawn(workflow, event);

    verify(workflowExecutionRepository, never()).save(any());
    verify(advanceKafkaTemplate, never()).send(any(), any(), any());
  }

  @Test
  void spawnShouldTolerateANullPayloadByStoringAnEmptyContext() {
    WorkflowExecutionEngine engine = buildEngine();
    Workflow workflow = workflowWithGraph(SIMPLE_TRIGGER_END_GRAPH);
    when(workflowExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    BusinessEvent event = BusinessEvent.builder().eventType("CART_ABANDONED").payload(null).build();

    engine.spawn(workflow, event);

    ArgumentCaptor<WorkflowExecution> captor = ArgumentCaptor.forClass(WorkflowExecution.class);
    verify(workflowExecutionRepository).save(captor.capture());
    assertEquals("{}", captor.getValue().getContextJson());
  }

  // -------------------------------------------------------------- advance()

  @Test
  void advanceShouldNoOpForAnUnknownExecutionId() {
    WorkflowExecutionEngine engine = buildEngine();
    when(workflowExecutionRepository.findById(999L)).thenReturn(Optional.empty());

    engine.advance(999L);

    verify(workflowExecutionRepository, never()).save(any());
    verify(workflowRepository, never()).findById(any());
  }

  @Test
  void advanceShouldNoOpWhenTheExecutionIsNotRunningOrAdvancing() {
    WorkflowExecutionEngine engine = buildEngine();
    WorkflowExecution execution = executionAt("w1", ExecutionStatus.COMPLETED);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));

    engine.advance(100L);

    verify(workflowExecutionRepository, never()).save(any());
    verify(workflowRepository, never()).findById(any());
  }

  @Test
  void advanceShouldProcessAnExecutionInAdvancingStatusJustLikeRunning() {
    NodeHandler endHandler = mockHandler("END", NodeOutcome.complete("done"));
    WorkflowExecutionEngine engine = buildEngine(endHandler);
    Workflow workflow = workflowWithGraph(SIMPLE_TRIGGER_END_GRAPH);
    WorkflowExecution execution = executionAt("t1", ExecutionStatus.ADVANCING);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

    engine.advance(100L);

    assertEquals(ExecutionStatus.COMPLETED, execution.getStatus());
  }

  @Test
  void advanceShouldFailTheExecutionWhenItsWorkflowNoLongerExists() {
    WorkflowExecutionEngine engine = buildEngine();
    WorkflowExecution execution = executionAt("w1", ExecutionStatus.RUNNING);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.empty());

    engine.advance(100L);

    assertEquals(ExecutionStatus.FAILED, execution.getStatus());
    verify(workflowExecutionRepository).save(execution);
    verify(executionLogService).error(eq(100L), isNull(), isNull(), contains("no longer exists"));
  }

  @Test
  void advanceShouldWalkThroughContinueOutcomesThenPersistOnceItSuspends() {
    NodeHandler notificationHandler = mockHandler("NOTIFICATION", NodeOutcome.continueTo("sent"));
    LocalDateTime wakeAt = LocalDateTime.now().plusHours(1);
    NodeHandler waitHandler = mockHandler("WAIT", NodeOutcome.suspendUntil(wakeAt, "waiting"));
    WorkflowExecutionEngine engine = buildEngine(notificationHandler, waitHandler);

    Workflow workflow = workflowWithGraph(GRAPH_TRIGGER_NOTIFICATION_WAIT);
    WorkflowExecution execution = executionAt("t1", ExecutionStatus.RUNNING);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

    engine.advance(100L);

    assertEquals(ExecutionStatus.WAITING, execution.getStatus());
    assertEquals("w1", execution.getCurrentNodeId());
    assertEquals(wakeAt, execution.getNextWakeAt());
    verify(workflowExecutionRepository, times(1)).save(execution);
    verify(executionLogService).info(eq(100L), eq("n1"), eq("NOTIFICATION"), eq("sent"));
    verify(executionLogService).info(eq(100L), eq("w1"), eq("WAIT"), eq("waiting"));
  }

  @Test
  void advanceShouldFollowAGatewayBranchThenCompleteAtEnd() {
    NodeHandler gatewayHandler = mockHandler("GATEWAY", NodeOutcome.continueBranch("yes", "branched"));
    NodeHandler endHandler = mockHandler("END", NodeOutcome.complete("done"));
    WorkflowExecutionEngine engine = buildEngine(gatewayHandler, endHandler);

    Workflow workflow = workflowWithGraph(GRAPH_TRIGGER_GATEWAY_END);
    WorkflowExecution execution = executionAt("t1", ExecutionStatus.RUNNING);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

    engine.advance(100L);

    assertEquals(ExecutionStatus.COMPLETED, execution.getStatus());
    assertEquals("e1", execution.getCurrentNodeId());
    assertNotNull(execution.getCompletedAt());
    verify(workflowExecutionRepository).save(execution);
  }

  @Test
  void advanceShouldFailTheExecutionWhenAHandlerReturnsAFailOutcome() {
    NodeHandler gatewayHandler = mockHandler("GATEWAY", NodeOutcome.fail("bad condition"));
    WorkflowExecutionEngine engine = buildEngine(gatewayHandler);
    Workflow workflow = workflowWithGraph(GRAPH_TRIGGER_TO_GATEWAY);
    WorkflowExecution execution = executionAt("t1", ExecutionStatus.RUNNING);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

    engine.advance(100L);

    assertEquals(ExecutionStatus.FAILED, execution.getStatus());
    verify(executionLogService).error(eq(100L), eq("g1"), eq("GATEWAY"), eq("bad condition"));
    verify(workflowExecutionRepository).save(execution);
  }

  @Test
  void advanceShouldConvertAnExceptionThrownByAHandlerIntoAFailedExecution() {
    NodeHandler gatewayHandler = mock(NodeHandler.class);
    when(gatewayHandler.nodeType()).thenReturn("GATEWAY");
    when(gatewayHandler.handle(any(), any(), any())).thenThrow(new IllegalStateException("boom"));
    WorkflowExecutionEngine engine = buildEngine(gatewayHandler);
    Workflow workflow = workflowWithGraph(GRAPH_TRIGGER_TO_GATEWAY);
    WorkflowExecution execution = executionAt("t1", ExecutionStatus.RUNNING);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

    engine.advance(100L);

    assertEquals(ExecutionStatus.FAILED, execution.getStatus());
    verify(executionLogService).error(eq(100L), eq("g1"), eq("GATEWAY"), contains("boom"));
    verify(workflowExecutionRepository).save(execution);
  }

  @Test
  void advanceShouldFailWhenTheCurrentNodeHasNoOutgoingEdge() {
    WorkflowExecutionEngine engine = buildEngine();
    Workflow workflow = workflowWithGraph(GRAPH_TRIGGER_TO_GATEWAY);
    // g1 exists in the graph but has no outgoing edge of its own.
    WorkflowExecution execution = executionAt("g1", ExecutionStatus.RUNNING);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

    engine.advance(100L);

    assertEquals(ExecutionStatus.FAILED, execution.getStatus());
    verify(executionLogService).error(eq(100L), eq("g1"), isNull(), contains("No outgoing edge"));
    verify(workflowExecutionRepository).save(execution);
  }

  @Test
  void advanceShouldFailWhenAnEdgeTargetsANodeThatDoesNotExist() {
    WorkflowExecutionEngine engine = buildEngine();
    Workflow workflow = workflowWithGraph(GRAPH_DANGLING_TARGET);
    WorkflowExecution execution = executionAt("t1", ExecutionStatus.RUNNING);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

    engine.advance(100L);

    assertEquals(ExecutionStatus.FAILED, execution.getStatus());
    verify(executionLogService).error(eq(100L), eq("t1"), isNull(), contains("ghost"));
    verify(workflowExecutionRepository).save(execution);
  }

  @Test
  void advanceShouldFailWhenNoHandlerIsRegisteredForTheTargetNodeType() {
    WorkflowExecutionEngine engine = buildEngine();
    Workflow workflow = workflowWithGraph(GRAPH_UNKNOWN_NODE_TYPE);
    WorkflowExecution execution = executionAt("t1", ExecutionStatus.RUNNING);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

    engine.advance(100L);

    assertEquals(ExecutionStatus.FAILED, execution.getStatus());
    verify(executionLogService).error(eq(100L), eq("x1"), eq("MYSTERY"), contains("No handler registered"));
    verify(workflowExecutionRepository).save(execution);
  }

  @Test
  void advanceShouldFailAfterExceedingTheMaxStepsGuardOnACyclicGraph() {
    NodeHandler loopingHandler = mockHandler("NOTIFICATION", NodeOutcome.continueTo("loop"));
    WorkflowExecutionEngine engine = buildEngine(loopingHandler);
    Workflow workflow = workflowWithGraph(GRAPH_CYCLE);
    WorkflowExecution execution = executionAt("t1", ExecutionStatus.RUNNING);
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

    engine.advance(100L);

    assertEquals(ExecutionStatus.FAILED, execution.getStatus());
    verify(executionLogService).error(eq(100L), isNull(), isNull(), contains("Exceeded 25 steps"));
    verify(workflowExecutionRepository, times(1)).save(execution);
  }

  @Test
  void advanceShouldToleratesMalformedContextJsonByStartingFromAnEmptyContext() {
    NodeHandler endHandler = mockHandler("END", NodeOutcome.complete("done"));
    WorkflowExecutionEngine engine = buildEngine(endHandler);
    Workflow workflow = workflowWithGraph(SIMPLE_TRIGGER_END_GRAPH);
    WorkflowExecution execution = WorkflowExecution.builder()
      .id(100L).workflowId(10L).workflowVersion(1)
      .status(ExecutionStatus.RUNNING).currentNodeId("t1").contextJson("{not valid json")
      .build();
    when(workflowExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));
    when(workflowRepository.findById(10L)).thenReturn(Optional.of(workflow));

    engine.advance(100L);

    assertEquals(ExecutionStatus.COMPLETED, execution.getStatus());
    assertEquals("{}", execution.getContextJson());
  }
}
