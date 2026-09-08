package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.DlqMessageResponse;
import com.eventflow.ingestion.dto.DlqReplayResponse;
import com.eventflow.ingestion.dto.InfrastructureHealthResponse;
import com.eventflow.ingestion.dto.ServiceHealthStatus;
import com.eventflow.ingestion.exception.DlqMessageNotFoundException;
import com.eventflow.ingestion.security.SecurityConfig;
import com.eventflow.ingestion.service.DlqManagementService;
import com.eventflow.ingestion.service.MonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest loads only the web layer (this controller + Spring MVC
 * infrastructure), not the full application context — MonitoringService and
 * DlqManagementService are mocked, so this never touches a real DataSource/
 * KafkaAdmin/RedisConnectionFactory/Postgres and needs no running
 * Postgres/Kafka/Redis.
 *
 * @Import(SecurityConfig.class) pulls in the REAL security filter chain
 * rather than mocking security away entirely. That matters here
 * specifically: this is a regression test for "/api/monitoring/** must
 * stay reachable with no login" (SecurityConfig's PUBLIC_PATTERNS) — if
 * someone later moves this path out of PUBLIC_PATTERNS, or the
 * .oauth2ResourceServer() wiring regresses, these requests would start
 * getting 401s and this test would fail loudly instead of silently.
 *
 * No running Keycloak is required to run this test: the JwtDecoder bean
 * that spring.security.oauth2.resourceserver.jwt.jwk-set-uri triggers is
 * only ever consulted when a request actually carries a Bearer token —
 * these plain, unauthenticated requests never reach that code path.
 */
@WebMvcTest(controllers = MonitoringController.class)
@Import(SecurityConfig.class)
class MonitoringControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private MonitoringService monitoringService;

  @MockBean
  private DlqManagementService dlqManagementService;

  @Test
  void shouldReturn200WithNoAuthenticationWhenAllDependenciesAreUp() throws Exception {
    InfrastructureHealthResponse healthy = InfrastructureHealthResponse.builder()
      .healthy(true)
      .database(ServiceHealthStatus.up("Operational (PostgreSQL)"))
      .kafka(ServiceHealthStatus.up("OK (1 broker(s))"))
      .redis(ServiceHealthStatus.up("Connected"))
      .build();
    when(monitoringService.getHealth()).thenReturn(healthy);

    mockMvc.perform(get("/api/monitoring/health"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.healthy").value(true))
      .andExpect(jsonPath("$.database.up").value(true))
      .andExpect(jsonPath("$.database.message").value("Operational (PostgreSQL)"));
  }

  @Test
  void shouldStillReturn200WithHealthyFalseWhenADependencyIsDown() throws Exception {
    // This is the exact scenario the user originally reported as broken:
    // stopping Postgres must flip healthy to false in the response body —
    // and per MonitoringController's own doc comment, the HTTP status
    // stays 200 either way, since a down dependency is data, not a
    // failure of this endpoint itself.
    InfrastructureHealthResponse degraded = InfrastructureHealthResponse.builder()
      .healthy(false)
      .database(ServiceHealthStatus.down("Connection refused"))
      .kafka(ServiceHealthStatus.up("OK (1 broker(s))"))
      .redis(ServiceHealthStatus.up("Connected"))
      .build();
    when(monitoringService.getHealth()).thenReturn(degraded);

    mockMvc.perform(get("/api/monitoring/health"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.healthy").value(false))
      .andExpect(jsonPath("$.database.up").value(false))
      .andExpect(jsonPath("$.database.message").value("DOWN: Connection refused"));
  }

  @Test
  void shouldReturnDlqMessagesFromTheRealBackingStoreNotAHardcodedList() throws Exception {
    DlqMessageResponse message = DlqMessageResponse.builder()
      .id("1")
      .recipient("user-42")
      .channel("EMAIL")
      .errorReason("SMTP 550 Invalid Recipient")
      .timestamp("10:14:22")
      .build();
    when(dlqManagementService.listMessages()).thenReturn(List.of(message));

    mockMvc.perform(get("/api/monitoring/dlq"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value("1"))
      .andExpect(jsonPath("$[0].recipient").value("user-42"))
      .andExpect(jsonPath("$[0].channel").value("EMAIL"));
  }

  @Test
  void deletingADlqMessageShouldReturn204AndDelegateToTheService() throws Exception {
    mockMvc.perform(delete("/api/monitoring/dlq/1"))
      .andExpect(status().isNoContent());

    verify(dlqManagementService).deleteMessage(1L);
  }

  @Test
  void deletingAnUnknownDlqMessageShouldReturn404() throws Exception {
    // Regression guard for the reported bug: a delete that silently no-ops
    // on an id the store doesn't have must surface as a 404, not a false
    // "success" that later gets misread as "it should be gone now."
    doThrow(new DlqMessageNotFoundException(999L)).when(dlqManagementService).deleteMessage(999L);

    mockMvc.perform(delete("/api/monitoring/dlq/999"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.message").value("DLQ message not found: 999"));
  }

  @Test
  void replayShouldReturnHowManyMessagesWereActuallyRepublished() throws Exception {
    when(dlqManagementService.replayAll()).thenReturn(DlqReplayResponse.builder().replayedCount(3).build());

    mockMvc.perform(post("/api/monitoring/dlq/replay"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.replayedCount").value(3));
  }
}
