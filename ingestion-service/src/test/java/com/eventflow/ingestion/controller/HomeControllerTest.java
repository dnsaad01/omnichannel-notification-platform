package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "/" is not in SecurityConfig's PUBLIC_PATTERNS, so — unlike a typical
 * unauthenticated landing page — it falls under .anyRequest().authenticated()
 * just like every other endpoint this project hasn't explicitly opted out.
 */
@WebMvcTest(controllers = HomeController.class)
@Import(SecurityConfig.class)
class HomeControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldRejectAnUnauthenticatedRequest() throws Exception {
    mockMvc.perform(get("/"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnServiceInfoWhenAuthenticated() throws Exception {
    mockMvc.perform(get("/").with(jwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.service").value("Omnichannel Notification Ingestion Service"))
      .andExpect(jsonPath("$.status").value("UP"));
  }
}
