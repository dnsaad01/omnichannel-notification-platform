package com.eventflow.ingestion.workflow.controller;

import com.eventflow.ingestion.workflow.service.EmailTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Email-open tracking pixel — this endpoint is what actually sets
 * context.email.opened.
 * NotificationNodeHandler embeds
 * <img src=".../api/tracking/open/{executionId}"> into EMAIL-channel
 * bodies (requires EmailService to send HTML — see its class-level note);
 * the recipient's mail client fetching that image on open is the signal.
 * A GATEWAY node downstream (config e.g. {"variable":"email.opened",
 * "operator":"equals","value":"true"}), reached after a WAIT node has
 * given the recipient time to actually open the message, is what a
 * workflow uses to branch on it.
 *
 * Deliberately never lets a tracking failure surface as anything other
 * than a normal image response: whatever happens in
 * EmailTrackingService#recordOpen (unknown execution, lock conflict,
 * malformed context), the pixel itself must always render, or mail
 * clients may show a broken-image icon in the recipient's inbox. All of
 * that error handling lives in EmailTrackingService, which swallows its
 * own exceptions — this controller doesn't even need a try/catch.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TrackingController {

  /** The smallest possible valid GIF: 1x1, fully transparent. Served as a
   *  literal byte array rather than a static resource so the whole feature
   *  stays self-contained in these two new files, with no static-file
   *  wiring to add. */
  private static final byte[] TRANSPARENT_PIXEL = Base64.getDecoder().decode(
    "R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==");

  private final EmailTrackingService emailTrackingService;

  @GetMapping(value = "/api/tracking/open/{executionId}", produces = MediaType.IMAGE_GIF_VALUE)
  public ResponseEntity<byte[]> trackOpen(@PathVariable Long executionId) {
    emailTrackingService.recordOpen(executionId);

    return ResponseEntity.ok()
      .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
      .contentType(MediaType.IMAGE_GIF)
      .body(TRANSPARENT_PIXEL);
  }
}
