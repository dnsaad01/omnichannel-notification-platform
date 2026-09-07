package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.InfrastructureHealthResponse;
import com.eventflow.ingestion.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the Angular Monitoring page (pages/monitoring) with real,
 * live-checked infrastructure status — see MonitoringService for what each
 * check actually does and why the old page could never detect an outage.
 *
 * Always returns 200, even when healthy=false — the response body IS the
 * status; a down dependency is data for the frontend to render, not an
 * HTTP-level failure of this endpoint itself.
 */
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

  private final MonitoringService monitoringService;

  @GetMapping("/health")
  public ResponseEntity<InfrastructureHealthResponse> getHealth() {
    return ResponseEntity.ok(monitoringService.getHealth());
  }
}
