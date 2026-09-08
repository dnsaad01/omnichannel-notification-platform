package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.ChannelStatistics;
import com.eventflow.ingestion.dto.StatisticsResponse;
import com.eventflow.ingestion.security.SecurityConfig;
import com.eventflow.ingestion.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StatisticsController.class)
@Import(SecurityConfig.class)
class StatisticsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private StatisticsService statisticsService;

  @Test
  void shouldRejectAnUnauthenticatedRequest() throws Exception {
    mockMvc.perform(get("/api/dashboard/statistics"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnTheFullStatisticsPayloadIncludingTheHonestNonTrackedClickRate() throws Exception {
    // Deliberately asserts on the literal "Non suivi" placeholder, not a
    // number — see StatisticsService's own class doc comment for why
    // click-through rate is never fabricated here.
    StatisticsResponse response = StatisticsResponse.builder()
      .totalSent("500")
      .deliveryRate("88.0%")
      .openRate("40.0%")
      .clickRate("Non suivi")
      .channels(List.of(ChannelStatistics.builder().name("Email").sent("500").delivered("440").rate("88.0%").build()))
      .build();
    when(statisticsService.getStatistics()).thenReturn(response);

    mockMvc.perform(get("/api/dashboard/statistics").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalSent").value("500"))
      .andExpect(jsonPath("$.clickRate").value("Non suivi"))
      .andExpect(jsonPath("$.channels[0].name").value("Email"));
  }
}
