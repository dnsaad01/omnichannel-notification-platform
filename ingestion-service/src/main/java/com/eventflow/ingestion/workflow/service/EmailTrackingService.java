package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.workflow.engine.ContextUtils;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Marks context.email.opened = true (plus an email.openedAt timestamp) on a
 * WorkflowExecution when its tracking pixel is fetched — see
 * TrackingController. Mirrors WorkflowExecutionEngine's own
 * readContext/writeContext handling of contextJson, but is kept as its own
 * small service rather than reusing the engine directly: this isn't an
 * advance() step, it never touches currentNodeId or the graph, it only
 * mutates context so a *later* GATEWAY node (evaluated the next time
 * advance() genuinely runs, e.g. after a WAIT node's timer fires) can see
 * the flag — see GatewayNodeHandler's own doc comment for that exact
 * pattern.
 *
 * Best-effort by design: TrackingController must always return the pixel
 * image no matter what happens here, so every failure mode (unknown
 * execution id, an optimistic-lock conflict racing a real advance() call,
 * malformed contextJson) is logged and swallowed rather than propagated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTrackingService {

  private static final TypeReference<Map<String, Object>> CONTEXT_TYPE = new TypeReference<>() {
  };

  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final WorkflowExecutionLogService executionLogService;
  private final ObjectMapper objectMapper;

  @Transactional
  public void recordOpen(Long executionId) {
    try {
      WorkflowExecution execution = workflowExecutionRepository.findById(executionId).orElse(null);
      if (execution == null) {
        log.debug("Tracking pixel fetched for unknown execution [{}] — ignoring", executionId);
        return;
      }

      Map<String, Object> context = readContext(execution);
      Object alreadyOpened = ContextUtils.resolve(context, "email.opened");
      ContextUtils.set(context, "email.opened", true);
      ContextUtils.set(context, "email.openedAt", LocalDateTime.now().toString());
      execution.setContextJson(objectMapper.writeValueAsString(context));
      workflowExecutionRepository.save(execution);

      if (!Boolean.TRUE.equals(alreadyOpened)) {
        executionLogService.info(execution.getId(), null, "TRACKING", "Pixel de suivi chargé — email marqué comme ouvert");
      }
    } catch (Exception e) {
      log.warn("Failed to record email open for execution [{}]: {}", executionId, e.getMessage());
    }
  }

  private Map<String, Object> readContext(WorkflowExecution execution) {
    String json = execution.getContextJson();
    if (json == null || json.isBlank()) {
      return new HashMap<>();
    }
    try {
      return objectMapper.readValue(json, CONTEXT_TYPE);
    } catch (Exception e) {
      log.warn("Execution [{}] has malformed contextJson — starting from an empty context for the tracking update", execution.getId());
      return new HashMap<>();
    }
  }
}
