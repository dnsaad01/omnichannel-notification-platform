package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.exception.WorkflowNotFoundException;
import com.eventflow.ingestion.exception.WorkflowValidationException;
import com.eventflow.ingestion.workflow.dto.WorkflowRequest;
import com.eventflow.ingestion.workflow.dto.WorkflowResponse;
import com.eventflow.ingestion.workflow.engine.graph.WorkflowGraph;
import com.eventflow.ingestion.workflow.model.Workflow;
import com.eventflow.ingestion.workflow.model.WorkflowStatus;
import com.eventflow.ingestion.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * CRUD + lifecycle (activate/deactivate/duplicate) for Workflow definitions.
 * Versioning policy: editing an ACTIVE workflow never mutates it in place —
 * it inserts a new DRAFT row (parentWorkflowId = the row being revised);
 * activating that draft archives the parent. In-flight WorkflowExecutions
 * stay pinned to whichever row id they started against, so they're never
 * affected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

  private static final String DEFAULT_DEFINITION = "{\"nodes\":[],\"edges\":[]}";

  private final WorkflowRepository workflowRepository;
  private final WorkflowGraphValidator workflowGraphValidator;

  public List<WorkflowResponse> findAll() {
    return workflowRepository.findAll().stream().map(this::toResponse).toList();
  }

  public WorkflowResponse findById(Long id) {
    return toResponse(getOrThrow(id));
  }

  public WorkflowResponse create(WorkflowRequest request) {
    Workflow workflow = Workflow.builder()
      .name(request.getName())
      .description(request.getDescription())
      .triggerEventType(request.getTriggerEventType())
      .definitionJson(resolveDefinition(request))
      .build();
    return toResponse(workflowRepository.save(workflow));
  }

  /**
   * On an ACTIVE workflow, this is copy-on-write: the live row is left
   * untouched and a new DRAFT (version + 1) is returned instead. Any other
   * status is edited in place — it isn't live yet, so there's nothing to
   * protect.
   */
  @Transactional
  public WorkflowResponse update(Long id, WorkflowRequest request) {
    Workflow existing = getOrThrow(id);

    if (existing.getStatus() == WorkflowStatus.ACTIVE) {
      Workflow draft = Workflow.builder()
        .name(request.getName())
        .description(request.getDescription())
        .triggerEventType(request.getTriggerEventType())
        .definitionJson(resolveDefinition(request))
        .status(WorkflowStatus.DRAFT)
        .version(existing.getVersion() + 1)
        .parentWorkflowId(existing.getId())
        .build();
      log.info("Workflow [{}] is ACTIVE — creating draft revision v{} instead of editing in place", id, draft.getVersion());
      return toResponse(workflowRepository.save(draft));
    }

    existing.setName(request.getName());
    existing.setDescription(request.getDescription());
    existing.setTriggerEventType(request.getTriggerEventType());
    if (request.getDefinitionJson() != null && !request.getDefinitionJson().isBlank()) {
      existing.setDefinitionJson(request.getDefinitionJson());
    }
    return toResponse(workflowRepository.save(existing));
  }

  public void delete(Long id) {
    Workflow workflow = getOrThrow(id);
    if (workflow.getStatus() == WorkflowStatus.ACTIVE) {
      throw new WorkflowValidationException(List.of("Cannot delete an ACTIVE workflow — deactivate it first"));
    }
    workflowRepository.deleteById(id);
  }

  /**
   * Validates the graph (WorkflowGraphValidator) and rejects activation if
   * another ACTIVE workflow already claims the same triggerEventType (a
   * workflow being re-activated as its own new version doesn't count as
   * "another" one).
   */
  @Transactional
  public WorkflowResponse activate(Long id) {
    Workflow workflow = getOrThrow(id);
    WorkflowGraph graph = WorkflowGraph.parse(workflow.getDefinitionJson());

    List<String> errors = new ArrayList<>(workflowGraphValidator.validate(graph));

    workflowRepository.findByStatusAndTriggerEventType(WorkflowStatus.ACTIVE, workflow.getTriggerEventType()).stream()
      .filter(other -> !other.getId().equals(workflow.getId()))
      .filter(other -> workflow.getParentWorkflowId() == null || !other.getId().equals(workflow.getParentWorkflowId()))
      .findFirst()
      .ifPresent(other -> errors.add("Another ACTIVE workflow ('" + other.getName() + "', id=" + other.getId()
        + ") already handles trigger event type '" + workflow.getTriggerEventType() + "'"));

    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors);
    }

    workflow.setStatus(WorkflowStatus.ACTIVE);
    workflowRepository.save(workflow);

    if (workflow.getParentWorkflowId() != null) {
      workflowRepository.findById(workflow.getParentWorkflowId())
        .filter(parent -> parent.getStatus() == WorkflowStatus.ACTIVE)
        .ifPresent(parent -> {
          parent.setStatus(WorkflowStatus.ARCHIVED);
          workflowRepository.save(parent);
          log.info("Archived previous workflow version [{}] in favor of [{}]", parent.getId(), workflow.getId());
        });
    }

    return toResponse(workflow);
  }

  /**
   * Does NOT touch in-flight WorkflowExecutions — it only stops this
   * workflow from matching new trigger events going forward.
   */
  public WorkflowResponse deactivate(Long id) {
    Workflow workflow = getOrThrow(id);
    workflow.setStatus(WorkflowStatus.DISABLED);
    return toResponse(workflowRepository.save(workflow));
  }

  public WorkflowResponse duplicate(Long id) {
    Workflow original = getOrThrow(id);
    Workflow copy = Workflow.builder()
      .name(original.getName() + " (copie)")
      .description(original.getDescription())
      .triggerEventType(original.getTriggerEventType())
      .definitionJson(original.getDefinitionJson())
      .status(WorkflowStatus.DRAFT)
      .version(1)
      .build();
    return toResponse(workflowRepository.save(copy));
  }

  private String resolveDefinition(WorkflowRequest request) {
    return request.getDefinitionJson() != null && !request.getDefinitionJson().isBlank()
      ? request.getDefinitionJson()
      : DEFAULT_DEFINITION;
  }

  private Workflow getOrThrow(Long id) {
    return workflowRepository.findById(id).orElseThrow(() -> new WorkflowNotFoundException(id));
  }

  private WorkflowResponse toResponse(Workflow workflow) {
    return WorkflowResponse.builder()
      .id(workflow.getId())
      .name(workflow.getName())
      .description(workflow.getDescription())
      .status(workflow.getStatus().name())
      .triggerEventType(workflow.getTriggerEventType())
      .version(workflow.getVersion())
      .parentWorkflowId(workflow.getParentWorkflowId())
      .definitionJson(workflow.getDefinitionJson())
      .createdAt(workflow.getCreatedAt())
      .updatedAt(workflow.getUpdatedAt())
      .build();
  }
}
