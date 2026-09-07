package com.eventflow.ingestion.workflow.controller;

import com.eventflow.ingestion.workflow.dto.WorkflowRequest;
import com.eventflow.ingestion.workflow.dto.WorkflowResponse;
import com.eventflow.ingestion.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD + lifecycle for Workflow definitions. See WorkflowService for the
 * versioning semantics behind PUT and POST /activate.
 */
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

  private final WorkflowService workflowService;

  @GetMapping
  public ResponseEntity<List<WorkflowResponse>> getAllWorkflows() {
    return ResponseEntity.ok(workflowService.findAll());
  }

  @GetMapping("/{id}")
  public ResponseEntity<WorkflowResponse> getWorkflowById(@PathVariable Long id) {
    return ResponseEntity.ok(workflowService.findById(id));
  }

  @PostMapping
  public ResponseEntity<WorkflowResponse> createWorkflow(@Valid @RequestBody WorkflowRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.create(request));
  }

  /** On an ACTIVE workflow this creates a new DRAFT revision instead of
   *  editing in place — see WorkflowService#update. */
  @PutMapping("/{id}")
  public ResponseEntity<WorkflowResponse> updateWorkflow(@PathVariable Long id, @Valid @RequestBody WorkflowRequest request) {
    return ResponseEntity.ok(workflowService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteWorkflow(@PathVariable Long id) {
    workflowService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/activate")
  public ResponseEntity<WorkflowResponse> activateWorkflow(@PathVariable Long id) {
    return ResponseEntity.ok(workflowService.activate(id));
  }

  @PostMapping("/{id}/deactivate")
  public ResponseEntity<WorkflowResponse> deactivateWorkflow(@PathVariable Long id) {
    return ResponseEntity.ok(workflowService.deactivate(id));
  }

  @PostMapping("/{id}/duplicate")
  public ResponseEntity<WorkflowResponse> duplicateWorkflow(@PathVariable Long id) {
    return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.duplicate(id));
  }
}
