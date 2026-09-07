package com.eventflow.ingestion.workflow.controller;

import com.eventflow.ingestion.exception.WorkflowValidationException;
import com.eventflow.ingestion.workflow.dto.WorkflowExecutionResponse;
import com.eventflow.ingestion.workflow.model.ExecutionStatus;
import com.eventflow.ingestion.workflow.service.WorkflowExecutionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only — executions are only ever created by WorkflowTriggerConsumer,
 * per the project's core rule that users configure workflows but never
 * launch them by hand. This is the Phase 1 way to actually observe the
 * engine working, ahead of the Phase 2 Exécutions UI.
 */
@RestController
@RequestMapping("/api/workflow-executions")
@RequiredArgsConstructor
public class WorkflowExecutionController {

  private final WorkflowExecutionQueryService workflowExecutionQueryService;

  @GetMapping
  public ResponseEntity<Page<WorkflowExecutionResponse>> listExecutions(
    @RequestParam(required = false) Long workflowId,
    @RequestParam(required = false) String status,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));

    if (workflowId != null) {
      return ResponseEntity.ok(workflowExecutionQueryService.findByWorkflowId(workflowId, pageable));
    }
    if (status != null && !status.isBlank()) {
      ExecutionStatus parsedStatus;
      try {
        parsedStatus = ExecutionStatus.valueOf(status.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new WorkflowValidationException(java.util.List.of("Unknown execution status: " + status));
      }
      return ResponseEntity.ok(workflowExecutionQueryService.findByStatus(parsedStatus, pageable));
    }
    return ResponseEntity.ok(workflowExecutionQueryService.findAll(pageable));
  }

  @GetMapping("/{id}")
  public ResponseEntity<WorkflowExecutionResponse> getExecutionDetail(@PathVariable Long id) {
    return ResponseEntity.ok(workflowExecutionQueryService.findDetailById(id));
  }
}
