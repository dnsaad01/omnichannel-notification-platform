package com.eventflow.ingestion.workflow.controller;

import com.eventflow.ingestion.security.SecurityConfig;
import com.eventflow.ingestion.workflow.service.EmailTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/tracking/** is one of the few paths SecurityConfig keeps public —
 * the recipient's mail client fetches this pixel anonymously and can never
 * present a Bearer token (see SecurityConfig's own PUBLIC_PATTERNS doc
 * comment and TrackingController's own class doc comment on why this must
 * always render the pixel, no matter what happens inside
 * EmailTrackingService).
 */
@WebMvcTest(controllers = TrackingController.class)
@Import(SecurityConfig.class)
class TrackingControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private EmailTrackingService emailTrackingService;

  @Test
  void shouldServeTheTrackingPixelWithNoAuthenticationRequired() throws Exception {
    mockMvc.perform(get("/api/tracking/open/42"))
      .andExpect(status().isOk())
      .andExpect(content().contentType(MediaType.IMAGE_GIF))
      .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate"));

    verify(emailTrackingService).recordOpen(42L);
  }

  @Test
  void shouldPropagateToTheGlobalHandlerIfEmailTrackingServiceEverStoppedSwallowingItsOwnExceptions() throws Exception {
    // TrackingController itself has deliberately no try/catch (see its class
    // doc comment) — the "always render the pixel" guarantee lives entirely
    // in EmailTrackingService#recordOpen swallowing its own failures. This
    // is a regression guard on that division of responsibility: if a future
    // change to EmailTrackingService ever let an exception through, this
    // proves it would surface as a 500 here rather than silently vanishing,
    // which should make such a regression obvious in that class's own tests.
    doThrow(new RuntimeException("db down")).when(emailTrackingService).recordOpen(99L);

    mockMvc.perform(get("/api/tracking/open/99"))
      .andExpect(status().isInternalServerError());
  }
}
