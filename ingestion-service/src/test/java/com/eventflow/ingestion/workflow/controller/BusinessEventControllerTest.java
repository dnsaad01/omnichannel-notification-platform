package com.eventflow.ingestion.workflow.controller;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.security.SecurityConfig;
import com.eventflow.ingestion.workflow.dto.BusinessEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/business-events/** is not in SecurityConfig's PUBLIC_PATTERNS (see
 * that class's own doc comment, which explicitly calls out that this now
 * breaks the "curl it directly" flow this controller's own javadoc still
 * advertises) — so, like TemplateControllerTest/WorkflowControllerTest, a
 * real JWT is required for every request here.
 */
@WebMvcTest(controllers = BusinessEventController.class)
@Import(SecurityConfig.class)
class BusinessEventControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private KafkaTemplate<String, BusinessEvent> kafkaTemplate;

  @Test
  void shouldRejectAnUnauthenticatedRequest() throws Exception {
    BusinessEvent event = BusinessEvent.builder().eventType("CART_ABANDONED").build();

    mockMvc.perform(post("/api/business-events/publish")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(event)))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldPublishTheEventOntoTheBusinessEventsTopicAndEchoItBack() throws Exception {
    BusinessEvent event = BusinessEvent.builder()
      .eventType("CART_ABANDONED")
      .payload(java.util.Map.of("recipientId", "client@example.com", "cartValue", 89.9))
      .build();

    mockMvc.perform(post("/api/business-events/publish").with(jwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(event)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("PUBLISHED"))
      .andExpect(jsonPath("$.eventType").value("CART_ABANDONED"))
      .andExpect(jsonPath("$.payload.recipientId").value("client@example.com"));

    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_BUSINESS_EVENTS), eq("CART_ABANDONED"), any());
  }

  @Test
  void shouldReturn400WhenEventTypeIsBlank() throws Exception {
    BusinessEvent event = BusinessEvent.builder().eventType("").build();

    mockMvc.perform(post("/api/business-events/publish").with(jwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(event)))
      .andExpect(status().isBadRequest());
  }
}
