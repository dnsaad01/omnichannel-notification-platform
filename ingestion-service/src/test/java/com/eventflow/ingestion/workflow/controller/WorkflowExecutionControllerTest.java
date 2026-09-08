package com.eventflow.ingestion.workflow.controller;

import com.eventflow.ingestion.exception.WorkflowExecutionNotFoundException;
import com.eventflow.ingestion.security.SecurityConfig;
import com.eventflow.ingestion.workflow.dto.WorkflowExecutionResponse;
import com.eventflow.ingestion.workflow.model.ExecutionStatus;
import com.eventflow.ingestion.workflow.service.WorkflowExecutionQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WorkflowExecutionController.class)
@Import(SecurityConfig.class)
class WorkflowExecutionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private WorkflowExecutionQueryService workflowExecutionQueryService;

  private WorkflowExecutionResponse aResponse(Long id) {
    return WorkflowExecutionResponse.builder().id(id).workflowId(1L).status("RUNNING").currentNodeId("n1").build();
  }

  @Test
  void shouldRejectAnUnauthenticatedRequest() throws Exception {
    mockMvc.perform(get("/api/workflow-executions"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldListAllExecutionsWhenNoFilterIsGiven() throws Exception {
    Page<WorkflowExecutionResponse> page = new PageImpl<>(List.of(aResponse(1L)));
    when(workflowExecutionQueryService.findAll(any())).thenReturn(page);

    mockMvc.perform(get("/api/workflow-executions").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].id").value(1));
  }

  @Test
  void shouldFilterByWorkflowIdWhenProvided() throws Exception {
    Page<WorkflowExecutionResponse> page = new PageImpl<>(List.of(aResponse(5L)));
    when(workflowExecutionQueryService.findByWorkflowId(eq(7L), any())).thenReturn(page);

    mockMvc.perform(get("/api/workflow-executions").param("workflowId", "7").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].id").value(5));
  }

  @Test
  void shouldFilterByStatusWhenProvided() throws Exception {
    Page<WorkflowExecutionResponse> page = new PageImpl<>(List.of(aResponse(9L)));
    when(workflowExecutionQueryService.findByStatus(eq(ExecutionStatus.WAITING), any())).thenReturn(page);

    mockMvc.perform(get("/api/workflow-executions").param("status", "waiting").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].id").value(9));
  }

  @Test
  void shouldReturn400ForAnUnknownStatusValue() throws Exception {
    mockMvc.perform(get("/api/workflow-executions").param("status", "NOT_A_REAL_STATUS").with(jwt()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("Unknown execution status: NOT_A_REAL_STATUS"));
  }

  @Test
  void shouldReturnExecutionDetailById() throws Exception {
    when(workflowExecutionQueryService.findDetailById(1L)).thenReturn(aResponse(1L));

    mockMvc.perform(get("/api/workflow-executions/1").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.currentNodeId").value("n1"));
  }

  @Test
  void shouldReturn404WhenTheExecutionDetailIsNotFound() throws Exception {
    when(workflowExecutionQueryService.findDetailById(404L)).thenThrow(new WorkflowExecutionNotFoundException(404L));

    mockMvc.perform(get("/api/workflow-executions/404").with(jwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.message").value("Workflow execution not found: 404"));
  }
}
