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

/**
 * Mirrors com.eventflow.ingestion.dto.DlqMessageResponse, served by
 * GET /api/monitoring/dlq (MonitoringController / DlqManagementService).
 * Backed by the dlq_messages table, itself populated by DlqMessageConsumer
 * listening on the real `notification-dlq` Kafka topic — this used to be a
 * hardcoded array on MonitoringComponent, which is why deleted entries kept
 * reappearing on refresh (a fresh component instance re-created the same 3
 * fake rows every time). It is real, persisted data now: deleting a row
 * removes it for good.
 */
export interface DlqMessage {
  id: string;
  recipient: string;
  channel: string;
  errorReason: string;
  timestamp: string;
}

/** Mirrors com.eventflow.ingestion.dto.DlqReplayResponse. */
export interface DlqReplayResponse {
  replayedCount: number;
}
