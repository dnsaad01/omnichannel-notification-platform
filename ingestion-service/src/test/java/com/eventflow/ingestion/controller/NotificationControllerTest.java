package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.NotificationRequest;
import com.eventflow.ingestion.exception.UnauthorizedException;
import com.eventflow.ingestion.security.SecurityConfig;
import com.eventflow.ingestion.service.NotificationIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/notifications/send(-test) are public per SecurityConfig — they're
 * already gated by their own X-API-KEY / client_apps check inside
 * NotificationIngestionService, not a user's JWT (see SecurityConfig's own
 * PUBLIC_PATTERNS doc comment) — so, unlike the workflow controllers, no
 * .with(jwt()) is used anywhere in this class.
 */
@WebMvcTest(controllers = NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private NotificationIngestionService notificationIngestionService;

  @Test
  void shouldAcceptAValidRequestWithNoAuthenticationRequiredAndForwardTheApiKey() throws Exception {
    NotificationRequest request = NotificationRequest.builder()
      .recipientId("user@example.com").channel("EMAIL").body("Hello").build();

    mockMvc.perform(post("/api/v1/notifications/send")
        .header("X-API-KEY", "test-key-123")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("ACCEPTED"));

    verify(notificationIngestionService).processAndPublish(eq("test-key-123"), any());
  }

  @Test
  void shouldDefaultTheApiKeyHeaderWhenItIsNotProvided() throws Exception {
    NotificationRequest request = NotificationRequest.builder()
      .recipientId("user@example.com").channel("SMS").build();

    mockMvc.perform(post("/api/v1/notifications/send")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk());

    verify(notificationIngestionService).processAndPublish(eq("default-api-key"), any());
  }

  @Test
  void shouldReturn400WhenRecipientIdIsBlank() throws Exception {
    NotificationRequest invalid = NotificationRequest.builder().recipientId("").channel("EMAIL").build();

    mockMvc.perform(post("/api/v1/notifications/send")
        .header("X-API-KEY", "test-key-123")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(invalid)))
      .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn401WhenTheApiKeyIsRejectedByTheIngestionService() throws Exception {
    NotificationRequest request = NotificationRequest.builder()
      .recipientId("user@example.com").channel("EMAIL").build();
    doThrow(new UnauthorizedException("Invalid API Key"))
      .when(notificationIngestionService).processAndPublish(any(), any());

    mockMvc.perform(post("/api/v1/notifications/send")
        .header("X-API-KEY", "bad-key")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.message").value("Invalid API Key"));
  }

  @Test
  void sendTestShouldAlwaysReturn200WithASuccessBody() throws Exception {
    mockMvc.perform(post("/api/v1/notifications/send-test")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("anything", "goes"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("SUCCESS"));
  }
}
