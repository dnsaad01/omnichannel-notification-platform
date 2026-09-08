package com.eventflow.ingestion.workflow.controller;

import com.eventflow.ingestion.exception.WorkflowNotFoundException;
import com.eventflow.ingestion.security.SecurityConfig;
import com.eventflow.ingestion.workflow.dto.WorkflowRequest;
import com.eventflow.ingestion.workflow.dto.WorkflowResponse;
import com.eventflow.ingestion.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WorkflowController.class)
@Import(SecurityConfig.class)
class WorkflowControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private WorkflowService workflowService;

  @Test
  void shouldRejectAnUnauthenticatedRequest() throws Exception {
    mockMvc.perform(get("/api/workflows"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnAllWorkflows() throws Exception {
    when(workflowService.findAll()).thenReturn(List.of(
      WorkflowResponse.builder().id(1L).name("Cart Abandoned").status("ACTIVE").build()
    ));

    mockMvc.perform(get("/api/workflows").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].name").value("Cart Abandoned"));
  }

  @Test
  void shouldReturn404ForAnUnknownWorkflowId() throws Exception {
    when(workflowService.findById(404L)).thenThrow(new WorkflowNotFoundException(404L));

    mockMvc.perform(get("/api/workflows/404").with(jwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.message").value("Workflow not found: 404"));
  }

  @Test
  void shouldReturn201AndTheCreatedWorkflow() throws Exception {
    WorkflowRequest request = WorkflowRequest.builder().name("New WF").triggerEventType("EVT").build();
    WorkflowResponse created = WorkflowResponse.builder().id(1L).name("New WF").status("DRAFT").build();
    when(workflowService.create(any())).thenReturn(created);

    mockMvc.perform(post("/api/workflows").with(jwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  @Test
  void shouldReturn400WhenTheWorkflowNameIsBlank() throws Exception {
    WorkflowRequest invalid = WorkflowRequest.builder().name("").triggerEventType("EVT").build();

    mockMvc.perform(post("/api/workflows").with(jwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(invalid)))
      .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn200WithTheUpdatedWorkflowOnUpdate() throws Exception {
    WorkflowRequest request = WorkflowRequest.builder().name("Renamed").triggerEventType("EVT").build();
    WorkflowResponse updated = WorkflowResponse.builder().id(1L).name("Renamed").status("DRAFT").build();
    when(workflowService.update(eq(1L), any())).thenReturn(updated);

    mockMvc.perform(put("/api/workflows/1").with(jwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Renamed"));
  }

  @Test
  void shouldReturn204OnSuccessfulDelete() throws Exception {
    mockMvc.perform(delete("/api/workflows/1").with(jwt()))
      .andExpect(status().isNoContent());
  }

  @Test
  void shouldReturn200WithTheActivatedWorkflow() throws Exception {
    WorkflowResponse activated = WorkflowResponse.builder().id(1L).status("ACTIVE").build();
    when(workflowService.activate(1L)).thenReturn(activated);

    mockMvc.perform(post("/api/workflows/1/activate").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void shouldReturn200WithTheDeactivatedWorkflow() throws Exception {
    WorkflowResponse deactivated = WorkflowResponse.builder().id(1L).status("DISABLED").build();
    when(workflowService.deactivate(1L)).thenReturn(deactivated);

    mockMvc.perform(post("/api/workflows/1/deactivate").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("DISABLED"));
  }

  @Test
  void shouldReturn201WithTheDuplicatedWorkflow() throws Exception {
    WorkflowResponse duplicated = WorkflowResponse.builder().id(2L).name("Cart Abandoned (copie)").status("DRAFT").build();
    when(workflowService.duplicate(1L)).thenReturn(duplicated);

    mockMvc.perform(post("/api/workflows/1/duplicate").with(jwt()))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.name").value("Cart Abandoned (copie)"));
  }
}
