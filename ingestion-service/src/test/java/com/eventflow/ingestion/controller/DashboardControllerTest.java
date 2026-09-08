package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.DashboardLogEntry;
import com.eventflow.ingestion.dto.DashboardStatsResponse;
import com.eventflow.ingestion.dto.NotificationResendResponse;
import com.eventflow.ingestion.dto.PagedLogsResponse;
import com.eventflow.ingestion.exception.NotificationLogNotFoundException;
import com.eventflow.ingestion.security.SecurityConfig;
import com.eventflow.ingestion.service.DashboardService;
import com.eventflow.ingestion.service.NotificationResendService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unlike MonitoringController, /api/dashboard/** is NOT in SecurityConfig's
 * PUBLIC_PATTERNS — every request here needs a Bearer token, which is why
 * every "happy path" test below rides in with .with(jwt()) (from
 * spring-security-test's SecurityMockMvcRequestPostProcessors). The first
 * test proves the inverse: with no token attached at all, the real
 * SecurityConfig filter chain (imported, not mocked) rejects the request
 * before it ever reaches DashboardController.
 */
@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private DashboardService dashboardService;

  @MockBean
  private NotificationResendService notificationResendService;

  @Test
  void shouldRejectAnUnauthenticatedRequestWithNoBearerToken() throws Exception {
    mockMvc.perform(get("/api/dashboard/stats"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnStatsWhenAuthenticated() throws Exception {
    when(dashboardService.getStats()).thenReturn(
      DashboardStatsResponse.builder().totalSent("1,234").successRate("92.5%").activeChannels("3").build()
    );

    mockMvc.perform(get("/api/dashboard/stats").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalSent").value("1,234"))
      .andExpect(jsonPath("$.successRate").value("92.5%"))
      .andExpect(jsonPath("$.activeChannels").value("3"));
  }

  @Test
  void shouldReturnRecentLogsAndDefaultTheLimitTo20WhenNotSpecified() throws Exception {
    when(dashboardService.getRecentLogs(20)).thenReturn(List.of(
      DashboardLogEntry.builder().id("NOTIF-1").user("a@example.com").channel("EMAIL").status("Delivered").time("2 min ago").build()
    ));

    mockMvc.perform(get("/api/dashboard/logs").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value("NOTIF-1"))
      .andExpect(jsonPath("$[0].status").value("Delivered"));
  }

  @Test
  void shouldForwardAnExplicitLimitQueryParamToTheService() throws Exception {
    when(dashboardService.getRecentLogs(5)).thenReturn(List.of());

    mockMvc.perform(get("/api/dashboard/logs").param("limit", "5").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$").isArray())
      .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void shouldRejectAnUnauthenticatedPagedLogsRequestWithNoBearerToken() throws Exception {
    mockMvc.perform(get("/api/dashboard/logs/page"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnAPageOfLogsDefaultingPageAndSizeWhenNotSpecified() throws Exception {
    when(dashboardService.getRecentLogsPage(0, 50)).thenReturn(
      PagedLogsResponse.builder()
        .content(List.of(DashboardLogEntry.builder().id("NOTIF-9").user("a@example.com").channel("EMAIL").status("Delivered").time("2 min ago").build()))
        .page(0).size(50).totalElements(312).totalPages(7)
        .build()
    );

    mockMvc.perform(get("/api/dashboard/logs/page").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].id").value("NOTIF-9"))
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(50))
      .andExpect(jsonPath("$.totalElements").value(312))
      .andExpect(jsonPath("$.totalPages").value(7));
  }

  @Test
  void shouldForwardExplicitPageAndSizeQueryParamsToTheService() throws Exception {
    when(dashboardService.getRecentLogsPage(3, 25)).thenReturn(
      PagedLogsResponse.builder().content(List.of()).page(3).size(25).totalElements(312).totalPages(13).build()
    );

    mockMvc.perform(get("/api/dashboard/logs/page").param("page", "3").param("size", "25").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(3))
      .andExpect(jsonPath("$.size").value(25));
  }

  @Test
  void shouldRejectAnUnauthenticatedResendRequestWithNoBearerToken() throws Exception {
    mockMvc.perform(post("/api/dashboard/logs/NOTIF-1/resend"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldResendANotificationLogWhenAuthenticated() throws Exception {
    when(notificationResendService.resend(eq("NOTIF-1"))).thenReturn(
      NotificationResendResponse.builder()
        .originalId("NOTIF-1")
        .recipient("a@example.com")
        .channel("EMAIL")
        .message("Notification relancée avec succès — un nouveau log apparaîtra sous peu.")
        .build()
    );

    mockMvc.perform(post("/api/dashboard/logs/NOTIF-1/resend").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.originalId").value("NOTIF-1"))
      .andExpect(jsonPath("$.recipient").value("a@example.com"))
      .andExpect(jsonPath("$.channel").value("EMAIL"));
  }

  @Test
  void shouldReturn404WhenResendingALogThatDoesNotExist() throws Exception {
    when(notificationResendService.resend(eq("NOTIF-999")))
      .thenThrow(new NotificationLogNotFoundException("NOTIF-999"));

    mockMvc.perform(post("/api/dashboard/logs/NOTIF-999/resend").with(jwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.message").value("Notification log not found: NOTIF-999"));
  }
}
