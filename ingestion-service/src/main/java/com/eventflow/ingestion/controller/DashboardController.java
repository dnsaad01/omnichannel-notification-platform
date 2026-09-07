package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.DashboardLogEntry;
import com.eventflow.ingestion.dto.DashboardStatsResponse;
import com.eventflow.ingestion.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Backs the Angular dashboard (services/dashboard.service.ts) with real
 * aggregates instead of hardcoded fallback data. Both endpoints read from
 * NotificationLog rows written by the three channel consumers.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  @GetMapping("/stats")
  public ResponseEntity<DashboardStatsResponse> getStats() {
    return ResponseEntity.ok(dashboardService.getStats());
  }

  @GetMapping("/logs")
  public ResponseEntity<List<DashboardLogEntry>> getRecentLogs(@RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(dashboardService.getRecentLogs(limit));
  }
}
