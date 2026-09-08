package com.eventflow.ingestion.simulator;

import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/simulator/** is not in SecurityConfig's PUBLIC_PATTERNS (see that
 * class's own doc comment: "Everything else ... is what the Angular app
 * calls directly, so those now require a valid Keycloak-issued JWT"), so —
 * like WorkflowControllerTest — every request here needs .with(jwt()).
 */
@WebMvcTest(controllers = SimulatorController.class)
@Import(SecurityConfig.class)
class SimulatorControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private KafkaSimulatorProducer simulatorProducer;

  @Test
  void shouldRejectAnUnauthenticatedRequest() throws Exception {
    mockMvc.perform(get("/api/v1/simulator/status"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void sendShouldDispatchAndReturnTheEvent() throws Exception {
    NotificationEvent event = NotificationEvent.builder().eventId("evt-1").recipientId("user@example.com").channel("EMAIL").build();
    when(simulatorProducer.sendSingleSimulatedEvent(any())).thenReturn(event);

    mockMvc.perform(post("/api/v1/simulator/send").with(jwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(event)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("SUCCESS"))
      .andExpect(jsonPath("$.event.eventId").value("evt-1"));
  }

  @Test
  void batchShouldReturnTheDispatchedCount() throws Exception {
    NotificationEvent e1 = NotificationEvent.builder().eventId("evt-1").build();
    NotificationEvent e2 = NotificationEvent.builder().eventId("evt-2").build();
    when(simulatorProducer.sendBatchSimulatedEvents(5)).thenReturn(List.of(e1, e2));

    mockMvc.perform(post("/api/v1/simulator/batch").param("count", "5").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.count").value(2));
  }

  @Test
  void startShouldReturnSuccessWhenTheSimulatorWasNotAlreadyRunning() throws Exception {
    when(simulatorProducer.startSimulation(10)).thenReturn(true);
    when(simulatorProducer.getStatus()).thenReturn(Map.of("active", true, "ratePerSecond", 10));

    mockMvc.perform(post("/api/v1/simulator/start").param("ratePerSec", "10").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("SUCCESS"));
  }

  @Test
  void startShouldReportAlreadyRunningWhenTheSimulatorRejectsTheStart() throws Exception {
    when(simulatorProducer.startSimulation(eq(10))).thenReturn(false);
    when(simulatorProducer.getStatus()).thenReturn(Map.of("active", true));

    mockMvc.perform(post("/api/v1/simulator/start").param("ratePerSec", "10").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("ALREADY_RUNNING"));
  }

  @Test
  void stopShouldReturnSuccessWhenSomethingWasActuallyStopped() throws Exception {
    when(simulatorProducer.stopSimulation()).thenReturn(true);
    when(simulatorProducer.getStatus()).thenReturn(Map.of("active", false));

    mockMvc.perform(post("/api/v1/simulator/stop").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("SUCCESS"));
  }

  @Test
  void stopShouldReportNotRunningWhenNothingWasStopped() throws Exception {
    when(simulatorProducer.stopSimulation()).thenReturn(false);
    when(simulatorProducer.getStatus()).thenReturn(Map.of("active", false));

    mockMvc.perform(post("/api/v1/simulator/stop").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("NOT_RUNNING"));
  }

  @Test
  void statusShouldReturnTheProducersCurrentStatus() throws Exception {
    when(simulatorProducer.getStatus()).thenReturn(Map.of("active", true, "totalSent", 42L));

    mockMvc.perform(get("/api/v1/simulator/status").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(true))
      .andExpect(jsonPath("$.totalSent").value(42));
  }
}
