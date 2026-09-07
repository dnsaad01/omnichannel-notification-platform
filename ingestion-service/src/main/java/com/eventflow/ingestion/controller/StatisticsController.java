package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.StatisticsResponse;
import com.eventflow.ingestion.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the Angular Statistics page (pages/statistics) with real aggregate
 * metrics — see StatisticsService for exactly what each number means and,
 * for openRate/clickRate specifically, what it does and doesn't cover.
 *
 * Path is /api/dashboard/statistics — under the existing /api/dashboard
 * prefix, not a new top-level one — purely so this falls under
 * SecurityConfig's existing "/api/dashboard/**".permitAll() matcher without
 * any security config change. Same underlying data domain as
 * DashboardController's own /stats and /logs (notification_logs, plus
 * workflow_executions for the open-rate signal), just a different rollup —
 * kept as its own controller/service pair as requested rather than folded
 * into DashboardController/DashboardService.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class StatisticsController {

  private final StatisticsService statisticsService;

  @GetMapping("/statistics")
  public ResponseEntity<StatisticsResponse> getStatistics() {
    return ResponseEntity.ok(statisticsService.getStatistics());
  }
}
