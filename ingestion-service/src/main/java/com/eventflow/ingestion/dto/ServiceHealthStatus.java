package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One infrastructure dependency's live status, as part of
 * InfrastructureHealthResponse (MonitoringController,
 * GET /api/monitoring/health). `up` drives the frontend's badge color
 * directly (never string-match `message`) — `message` is just the
 * human-readable detail shown next to it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceHealthStatus {
  private boolean up;
  private String message;

  public static ServiceHealthStatus up(String message) {
    return ServiceHealthStatus.builder().up(true).message(message).build();
  }

  public static ServiceHealthStatus down(String message) {
    return ServiceHealthStatus.builder().up(false).message("DOWN: " + message).build();
  }
}
