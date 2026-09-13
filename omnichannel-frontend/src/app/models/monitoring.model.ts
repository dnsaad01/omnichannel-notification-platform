/**
 * Mirrors the backend's real DTOs exactly:
 * com.eventflow.ingestion.dto.InfrastructureHealthResponse /
 * ServiceHealthStatus, served by GET /api/monitoring/health
 * (MonitoringController). `up` is what should drive any color/badge logic
 * — never string-match `message`, it's just the human-readable detail.
 */
export interface ServiceHealthStatus {
  up: boolean;
  message: string;
}

export interface InfrastructureHealthResponse {
  healthy: boolean;
  database: ServiceHealthStatus;
  kafka: ServiceHealthStatus;
  redis: ServiceHealthStatus;
}
