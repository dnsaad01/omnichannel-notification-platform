/**
 * Mirrors the backend contract exactly: com.eventflow.ingestion.dto.NotificationRequest.
 * recipientId and channel are @NotBlank server-side — omitting either yields an HTTP 400.
 *
 * subject/body now have dedicated slots on the DTO and are forwarded onto the
 * Kafka NotificationEvent by NotificationIngestionService, which the channel
 * consumers (Email/Push) render directly — no need to nest them in `payload`.
 */
export interface NotificationRequest {
  recipientId: string;
  channel: string;
  priority?: string;
  templateId?: string;
  subject?: string;
  body?: string;
  payload?: Record<string, unknown>;
}
