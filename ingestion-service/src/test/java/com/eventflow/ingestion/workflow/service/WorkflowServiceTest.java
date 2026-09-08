package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.exception.WorkflowNotFoundException;
import com.eventflow.ingestion.exception.WorkflowValidationException;
import com.eventflow.ingestion.workflow.dto.WorkflowRequest;
import com.eventflow.ingestion.workflow.dto.WorkflowResponse;
import com.eventflow.ingestion.workflow.model.Workflow;
import com.eventflow.ingestion.workflow.model.WorkflowStatus;
import com.eventflow.ingestion.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers WorkflowService's real business rules end to end against an
 * in-memory graph — WorkflowGraphValidator itself is exercised for real
 * here (not stubbed to always pass), since a validator mocked to always
 * return "no errors" would make these tests blind to the exact activation
 * rules they're supposed to prove (see the architecture-plan citations in
 * WorkflowService's own class/method doc comments).
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

  private static final String VALID_GRAPH = """
    {
      "nodes": [
        {"id": "t1", "type": "TRIGGER"},
        {"id": "e1", "type": "END"}
      ],
      "edges": [
        {"id": "edge-1", "source": "t1", "target": "e1"}
      ]
    }
    """;

  @Mock
  private WorkflowRepository workflowRepository;

  private WorkflowService workflowService;

  @BeforeEach
  void setUp() {
    // The real validator, not a mock — see class doc comment above.
    workflowService = new WorkflowService(workflowRepository, new WorkflowGraphValidator());
  }

  private Workflow aWorkflow(WorkflowStatus status) {
    return Workflow.builder()
      .id(1L).name("Cart Abandoned").triggerEventType("CART_ABANDONED")
      .status(status).version(1).definitionJson(VALID_GRAPH)
      .build();
  }

  @Test
  void createShouldPersistWithTheDefaultEmptyGraphWhenNoDefinitionJsonIsGiven() {
    when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    WorkflowRequest request = WorkflowRequest.builder().name("New").triggerEventType("EVT").build();

    WorkflowResponse response = workflowService.create(request);

    ArgumentCaptor<Workflow> captor = ArgumentCaptor.forClass(Workflow.class);
    verify(workflowRepository).save(captor.capture());
    assertEquals("{\"nodes\":[],\"edges\":[]}", captor.getValue().getDefinitionJson());
    assertEquals("New", response.getName());
  }

  @Test
  void findByIdShouldThrowWorkflowNotFoundExceptionForAnUnknownId() {
    when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(WorkflowNotFoundException.class, () -> workflowService.findById(99L));
  }

  @Test
  void updateOnADraftWorkflowShouldEditInPlace() {
    Workflow draft = aWorkflow(WorkflowStatus.DRAFT);
    when(workflowRepository.findById(1L)).thenReturn(Optional.of(draft));
    when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    WorkflowRequest request = WorkflowRequest.builder()
      .name("Renamed").triggerEventType("CART_ABANDONED").definitionJson(VALID_GRAPH).build();

    WorkflowResponse response = workflowService.update(1L, request);

    assertEquals(1L, response.getId());
    assertEquals("Renamed", response.getName());
    verify(workflowRepository, times(1)).save(any());
  }

  @Test
  void updateOnAnActiveWorkflowShouldCreateADraftRevisionInsteadOfEditingInPlace() {
    Workflow active = aWorkflow(WorkflowStatus.ACTIVE);
    when(workflowRepository.findById(1L)).thenReturn(Optional.of(active));
    when(workflowRepository.save(any())).thenAnswer(inv -> {
      Workflow w = inv.getArgument(0);
      w.setId(2L);
      return w;
    });

    WorkflowRequest request = WorkflowRequest.builder()
      .name("Cart Abandoned v2").triggerEventType("CART_ABANDONED").definitionJson(VALID_GRAPH).build();

    WorkflowResponse response = workflowService.update(1L, request);

    ArgumentCaptor<Workflow> captor = ArgumentCaptor.forClass(Workflow.class);
    verify(workflowRepository).save(captor.capture());

    Workflow saved = captor.getValue();
    assertEquals(WorkflowStatus.DRAFT, saved.getStatus());
    assertEquals(2, saved.getVersion());
    assertEquals(1L, saved.getParentWorkflowId());
    // The original row itself is never saved/mutated in place.
    assertEquals(2L, response.getId());
  }

  @Test
  void deleteShouldRejectAnActiveWorkflow() {
    when(workflowRepository.findById(1L)).thenReturn(Optional.of(aWorkflow(WorkflowStatus.ACTIVE)));

    WorkflowValidationException ex = assertThrows(WorkflowValidationException.class,
      () -> workflowService.delete(1L));

    assertTrue(ex.getErrors().get(0).contains("deactivate it first"));
    verify(workflowRepository, never()).deleteById(any());
  }

  @Test
  void deleteShouldRemoveANonActiveWorkflow() {
    when(workflowRepository.findById(1L)).thenReturn(Optional.of(aWorkflow(WorkflowStatus.DRAFT)));

    workflowService.delete(1L);

    verify(workflowRepository).deleteById(1L);
  }

  @Test
  void activateShouldRejectAGraphWithNoTriggerNode() {
    Workflow workflow = aWorkflow(WorkflowStatus.DRAFT);
    workflow.setDefinitionJson("{\"nodes\":[{\"id\":\"e1\",\"type\":\"END\"}],\"edges\":[]}");
    when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

    WorkflowValidationException ex = assertThrows(WorkflowValidationException.class,
      () -> workflowService.activate(1L));

    assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("no TRIGGER node")));
    verify(workflowRepository, never()).save(any());
  }

  @Test
  void activateShouldRejectWhenAnotherActiveWorkflowAlreadyOwnsTheSameTriggerEventType() {
    Workflow workflow = aWorkflow(WorkflowStatus.DRAFT);
    Workflow conflicting = Workflow.builder()
      .id(5L).name("Existing Active").triggerEventType("CART_ABANDONED")
      .status(WorkflowStatus.ACTIVE).version(1).build();

    when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));
    when(workflowRepository.findByStatusAndTriggerEventType(WorkflowStatus.ACTIVE, "CART_ABANDONED"))
      .thenReturn(List.of(conflicting));

    WorkflowValidationException ex = assertThrows(WorkflowValidationException.class,
      () -> workflowService.activate(1L));

    assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("Existing Active")));
    verify(workflowRepository, never()).save(any());
  }

  @Test
  void activateShouldSucceedAndArchiveTheParentWhenActivatingADraftRevision() {
    Workflow parent = aWorkflow(WorkflowStatus.ACTIVE);
    Workflow draftRevision = Workflow.builder()
      .id(2L).name("Cart Abandoned").triggerEventType("CART_ABANDONED")
      .status(WorkflowStatus.DRAFT).version(2).parentWorkflowId(1L).definitionJson(VALID_GRAPH)
      .build();

    when(workflowRepository.findById(2L)).thenReturn(Optional.of(draftRevision));
    // No conflicting ACTIVE workflow other than the parent itself, which is
    // explicitly exempted since it's this very revision's own predecessor.
    when(workflowRepository.findByStatusAndTriggerEventType(WorkflowStatus.ACTIVE, "CART_ABANDONED"))
      .thenReturn(List.of(parent));
    when(workflowRepository.findById(1L)).thenReturn(Optional.of(parent));
    when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    WorkflowResponse response = workflowService.activate(2L);

    assertEquals("ACTIVE", response.getStatus());
    verify(workflowRepository).save(draftRevision);
    assertEquals(WorkflowStatus.ARCHIVED, parent.getStatus());
    verify(workflowRepository, times(2)).save(any());
  }

  @Test
  void deactivateShouldSetStatusToDisabledWithoutTouchingInFlightExecutions() {
    Workflow active = aWorkflow(WorkflowStatus.ACTIVE);
    when(workflowRepository.findById(1L)).thenReturn(Optional.of(active));
    when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    WorkflowResponse response = workflowService.deactivate(1L);

    assertEquals("DISABLED", response.getStatus());
  }

  @Test
  void duplicateShouldCreateAFreshDraftAtVersionOneRegardlessOfTheOriginalsVersion() {
    Workflow original = aWorkflow(WorkflowStatus.ACTIVE);
    original.setVersion(7);
    when(workflowRepository.findById(1L)).thenReturn(Optional.of(original));
    when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    WorkflowResponse response = workflowService.duplicate(1L);

    assertEquals("Cart Abandoned (copie)", response.getName());
    assertEquals("DRAFT", response.getStatus());

    ArgumentCaptor<Workflow> captor = ArgumentCaptor.forClass(Workflow.class);
    verify(workflowRepository).save(captor.capture());
    assertEquals(1, captor.getValue().getVersion());
    assertNull(captor.getValue().getParentWorkflowId());
  }
}
