package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.DashboardLogEntry;
import com.eventflow.ingestion.dto.DashboardStatsResponse;
import com.eventflow.ingestion.dto.NotificationResendResponse;
import com.eventflow.ingestion.dto.PagedLogsResponse;
import com.eventflow.ingestion.service.DashboardService;
import com.eventflow.ingestion.service.NotificationResendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Backs the Angular dashboard (services/dashboard.service.ts) with real
 * aggregates instead of hardcoded fallback data. GET endpoints read from
 * NotificationLog rows written by the three channel consumers.
 *
 * GET /logs stays a flat, capped list (unchanged) for the Dashboard page's
 * small recent-activity widget. GET /logs/page is the real paginated
 * endpoint backing the Notifications page's "Journal des envois" table and
 * its Pagination Controls — see DashboardService#getRecentLogsPage.
 *
 * POST /logs/{id}/resend backs the Notifications page's "Relancer la
 * notification" button — see NotificationResendService's doc comment for
 * why this replaced a 100%-client-side fake simulation.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;
  private final NotificationResendService notificationResendService;

  @GetMapping("/stats")
  public ResponseEntity<DashboardStatsResponse> getStats() {
    return ResponseEntity.ok(dashboardService.getStats());
  }

  @GetMapping("/logs")
  public ResponseEntity<List<DashboardLogEntry>> getRecentLogs(@RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(dashboardService.getRecentLogs(limit));
  }

  @GetMapping("/logs/page")
  public ResponseEntity<PagedLogsResponse> getRecentLogsPage(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok(dashboardService.getRecentLogsPage(page, size));
  }

  @PostMapping("/logs/{id}/resend")
  public ResponseEntity<NotificationResendResponse> resendLog(@PathVariable String id) {
    return ResponseEntity.ok(notificationResendService.resend(id));
  }
}
