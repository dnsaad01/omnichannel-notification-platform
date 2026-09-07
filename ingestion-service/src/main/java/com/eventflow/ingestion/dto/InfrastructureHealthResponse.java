package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for GET /api/monitoring/health (MonitoringController). `healthy`
 * is true only when all three dependencies are actually reachable right
 * now — see MonitoringService for what "reachable" means for each one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfrastructureHealthResponse {
  private boolean healthy;
  private ServiceHealthStatus database;
  private ServiceHealthStatus kafka;
  private ServiceHealthStatus redis;
}
