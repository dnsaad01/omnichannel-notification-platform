package com.eventflow.ingestion.workflow.engine;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.workflow.dto.BusinessEvent;
import com.eventflow.ingestion.workflow.dto.WorkflowAdvanceMessage;
import com.eventflow.ingestion.workflow.engine.graph.EdgeDef;
import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.engine.graph.WorkflowGraph;
import com.eventflow.ingestion.workflow.engine.handler.NodeHandler;
import com.eventflow.ingestion.workflow.engine.handler.NodeOutcome;
import com.eventflow.ingestion.workflow.model.ExecutionStatus;
import com.eventflow.ingestion.workflow.model.Workflow;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionRepository;
import com.eventflow.ingestion.workflow.repository.WorkflowRepository;
import com.eventflow.ingestion.workflow.service.WorkflowExecutionLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns a WorkflowExecution's whole lifecycle: {@link #spawn} creates one
 * (called by WorkflowTriggerConsumer on a fresh trigger match) and
 * {@link #advance} is the advance-consumer's actual execution loop —
 * one call is one "tick": it re-enters the
 * execution at its currentNodeId and walks forward, node by node, until it
 * hits a suspend point (WAIT), a terminal state (COMPLETE/FAIL), or the step
 * cap (a guard against a malformed cyclic graph spinning forever).
 *
 * Both methods are @Transactional — note that this only works because
 * callers invoke them from a *different* Spring bean (WorkflowTriggerConsumer,
 * WorkflowAdvanceConsumer): Spring's proxy-based @Transactional does not
 * apply to self-invocation, so this logic deliberately does not live as a
 * private/package-private method inside the consumers themselves.
 *
 * advance() is at-least-once / idempotent by design: every call re-reads the
 * execution row and no-ops if it isn't RUNNING/ADVANCING, so a redelivered
 * Kafka message (or the wait-scheduler racing a manual retry) can never
 * double-run a step. Node handlers never persist anything themselves — this
 * class is the single place a NodeOutcome turns into a saved row, so
 * behavior can't drift between handlers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionEngine {

  private static final int MAX_STEPS_PER_INVOCATION = 25;
  private static final TypeReference<Map<String, Object>> CONTEXT_TYPE = new TypeReference<>() {
  };

  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final WorkflowRepository workflowRepository;
  private final WorkflowExecutionLogService executionLogService;
  private final ObjectMapper objectMapper;
  private final List<NodeHandler> nodeHandlers;
  private final KafkaTemplate<String, WorkflowAdvanceMessage> advanceKafkaTemplate;

  private Map<String, NodeHandler> handlersByType;

  @PostConstruct
  void indexHandlers() {
    handlersByType = nodeHandlers.stream()
      .collect(Collectors.toMap(h -> h.nodeType().toUpperCase(), Function.identity()));
    log.info("Workflow engine registered node handlers for types: {}", handlersByType.keySet());
  }

  /**
   * Creates a WorkflowExecution for one ACTIVE workflow matching an incoming
   * business event, and kicks off its first advance() by publishing to the
   * internal advance topic. Called once per matched workflow from
   * WorkflowTriggerConsumer.
   */
  @Transactional
  public void spawn(Workflow workflow, BusinessEvent event) {
    WorkflowGraph graph = WorkflowGraph.parse(workflow.getDefinitionJson());
    NodeDef triggerNode = graph.findTriggerNode().orElse(null);
    if (triggerNode == null) {
      log.error("ACTIVE workflow [{}] has no TRIGGER node in its graph — this should have been caught at activation time, skipping", workflow.getId());
      return;
    }

    WorkflowExecution execution = WorkflowExecution.builder()
      .workflowId(workflow.getId())
      .workflowVersion(workflow.getVersion())
      .status(ExecutionStatus.RUNNING)
      .currentNodeId(triggerNode.getId())
      .contextJson(writeContext(event.getPayload() != null ? event.getPayload() : Map.of()))
      .build();
    execution = workflowExecutionRepository.save(execution);

    executionLogService.info(execution.getId(), null, null, "Événement reçu depuis Kafka : " + event.getEventType());
    executionLogService.info(execution.getId(), triggerNode.getId(), "TRIGGER", "Trigger exécuté");

    log.info("Spawned execution [{}] for workflow [{}] ('{}') on event [{}]", execution.getId(), workflow.getId(), workflow.getName(), event.getEventType());
    advanceKafkaTemplate.send(KafkaTopicConfig.TOPIC_WORKFLOW_ADVANCE, String.valueOf(execution.getId()),
      WorkflowAdvanceMessage.builder().executionId(execution.getId()).build());
  }

  @Transactional
  public void advance(Long executionId) {
    WorkflowExecution execution = workflowExecutionRepository.findById(executionId).orElse(null);
    if (execution == null) {
      log.warn("Advance requested for unknown execution [{}] — ignoring", executionId);
      return;
    }
    if (execution.getStatus() != ExecutionStatus.RUNNING && execution.getStatus() != ExecutionStatus.ADVANCING) {
      log.debug("Execution [{}] is {} — ignoring redundant/late advance message", executionId, execution.getStatus());
      return;
    }

    Workflow workflow = workflowRepository.findById(execution.getWorkflowId()).orElse(null);
    if (workflow == null) {
      execution.setStatus(ExecutionStatus.FAILED);
      executionLogService.error(execution.getId(), null, null,
        "Workflow [" + execution.getWorkflowId() + "] referenced by this execution no longer exists");
      workflowExecutionRepository.save(execution);
      return;
    }

    WorkflowGraph graph = WorkflowGraph.parse(workflow.getDefinitionJson());
    Map<String, Object> context = readContext(execution);

    String currentNodeId = execution.getCurrentNodeId();
    String branch = null;
    int steps = 0;

    while (true) {
      if (++steps > MAX_STEPS_PER_INVOCATION) {
        fail(execution, null, null, "Exceeded " + MAX_STEPS_PER_INVOCATION + " steps in a single advance() call — check for a cyclic graph");
        break;
      }

      Optional<EdgeDef> edgeOpt = graph.outgoingEdge(currentNodeId, branch);
      if (edgeOpt.isEmpty()) {
        fail(execution, currentNodeId, null, "No outgoing edge from node [" + currentNodeId + "]"
          + (branch != null ? " for branch '" + branch + "'" : ""));
        break;
      }
      EdgeDef edge = edgeOpt.get();

      Optional<NodeDef> targetOpt = graph.findNode(edge.getTarget());
      if (targetOpt.isEmpty()) {
        fail(execution, currentNodeId, null, "Edge [" + edge.getId() + "] targets a node that doesn't exist: " + edge.getTarget());
        break;
      }
      NodeDef target = targetOpt.get();

      NodeHandler handler = handlersByType.get(target.getType() != null ? target.getType().toUpperCase() : "");
      if (handler == null) {
        fail(execution, target.getId(), target.getType(), "No handler registered for node type [" + target.getType() + "]");
        break;
      }

      NodeOutcome outcome;
      try {
        outcome = handler.handle(execution, target, context);
      } catch (Exception ex) {
        log.error("Node [{}] ({}) threw while advancing execution [{}]", target.getId(), target.getType(), execution.getId(), ex);
        fail(execution, target.getId(), target.getType(), "Node threw an exception: " + ex.getMessage());
        break;
      }

      currentNodeId = target.getId();

      switch (outcome.getType()) {
        case CONTINUE -> {
          executionLogService.info(execution.getId(), target.getId(), target.getType(), outcome.getLogMessage());
          branch = outcome.getBranch();
        }
        case SUSPEND -> {
          executionLogService.info(execution.getId(), target.getId(), target.getType(), outcome.getLogMessage());
          execution.setStatus(ExecutionStatus.WAITING);
          execution.setCurrentNodeId(currentNodeId);
          execution.setNextWakeAt(outcome.getWakeAt());
          persist(execution, context);
          return;
        }
        case COMPLETE -> {
          executionLogService.info(execution.getId(), target.getId(), target.getType(), outcome.getLogMessage());
          execution.setStatus(ExecutionStatus.COMPLETED);
          execution.setCurrentNodeId(currentNodeId);
          execution.setCompletedAt(LocalDateTime.now());
          persist(execution, context);
          return;
        }
        case FAIL -> {
          fail(execution, target.getId(), target.getType(), outcome.getErrorMessage());
          persist(execution, context);
          return;
        }
      }
    }

    persist(execution, context);
  }

  private void fail(WorkflowExecution execution, String nodeId, String nodeType, String message) {
    execution.setStatus(ExecutionStatus.FAILED);
    executionLogService.error(execution.getId(), nodeId, nodeType, message);
  }

  private void persist(WorkflowExecution execution, Map<String, Object> context) {
    execution.setContextJson(writeContext(context));
    workflowExecutionRepository.save(execution);
  }

  private Map<String, Object> readContext(WorkflowExecution execution) {
    String json = execution.getContextJson();
    if (json == null || json.isBlank()) {
      return new HashMap<>();
    }
    try {
      return objectMapper.readValue(json, CONTEXT_TYPE);
    } catch (JsonProcessingException e) {
      log.warn("Execution [{}] has malformed contextJson — starting from an empty context: {}", execution.getId(), e.getMessage());
      return new HashMap<>();
    }
  }

  private String writeContext(Map<String, Object> context) {
    try {
      return objectMapper.writeValueAsString(context);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize execution context: " + e.getMessage(), e);
    }
  }
}
