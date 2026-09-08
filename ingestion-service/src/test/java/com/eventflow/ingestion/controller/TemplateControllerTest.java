package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.exception.TemplateNotFoundException;
import com.eventflow.ingestion.security.SecurityConfig;
import com.eventflow.ingestion.workflow.dto.TemplateRequest;
import com.eventflow.ingestion.workflow.dto.TemplateResponse;
import com.eventflow.ingestion.workflow.service.NotificationTemplateService;
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

/**
 * Covers the full CRUD surface of /api/templates plus two error paths that
 * exercise GlobalExceptionHandler end to end: a 404 from a real
 * TemplateNotFoundException thrown by the (mocked) service, and a 400 from
 * bean validation on the request body itself (never reaches the service).
 * SecurityConfig is imported for real, same as the other controller tests —
 * /api/templates/** is not in PUBLIC_PATTERNS, so every request here needs
 * .with(jwt()).
 */
@WebMvcTest(controllers = TemplateController.class)
@Import(SecurityConfig.class)
class TemplateControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private NotificationTemplateService notificationTemplateService;

  @Test
  void shouldRejectAnUnauthenticatedRequest() throws Exception {
    mockMvc.perform(get("/api/templates"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturn201AndTheSavedTemplateOnCreate() throws Exception {
    TemplateRequest request = TemplateRequest.builder().name("Welcome").channel("email").body("Hi there").build();
    TemplateResponse saved = TemplateResponse.builder().id(1L).name("Welcome").channel("EMAIL").body("Hi there").status("ACTIVE").build();
    when(notificationTemplateService.create(any())).thenReturn(saved);

    mockMvc.perform(post("/api/templates").with(jwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.channel").value("EMAIL"));
  }

  @Test
  void shouldReturn400WhenNameIsBlank() throws Exception {
    TemplateRequest invalid = TemplateRequest.builder().name("").channel("email").body("Hi").build();

    mockMvc.perform(post("/api/templates").with(jwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(invalid)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void shouldReturnAllTemplates() throws Exception {
    when(notificationTemplateService.findAll()).thenReturn(List.of(
      TemplateResponse.builder().id(1L).name("Welcome").channel("EMAIL").status("ACTIVE").build()
    ));

    mockMvc.perform(get("/api/templates").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].name").value("Welcome"));
  }

  @Test
  void shouldReturn404WhenGettingAnUnknownTemplateId() throws Exception {
    when(notificationTemplateService.findById(404L)).thenThrow(new TemplateNotFoundException(404L));

    mockMvc.perform(get("/api/templates/404").with(jwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.status").value(404))
      .andExpect(jsonPath("$.message").value("Template not found: 404"));
  }

  @Test
  void shouldReturn200WithTheUpdatedTemplateOnUpdate() throws Exception {
    TemplateRequest request = TemplateRequest.builder().name("Renamed").channel("sms").body("New body").build();
    TemplateResponse updated = TemplateResponse.builder().id(1L).name("Renamed").channel("SMS").body("New body").status("ACTIVE").build();
    when(notificationTemplateService.update(eq(1L), any())).thenReturn(updated);

    mockMvc.perform(put("/api/templates/1").with(jwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Renamed"));
  }

  @Test
  void shouldReturn204OnSuccessfulDelete() throws Exception {
    mockMvc.perform(delete("/api/templates/1").with(jwt()))
      .andExpect(status().isNoContent());
  }
}
