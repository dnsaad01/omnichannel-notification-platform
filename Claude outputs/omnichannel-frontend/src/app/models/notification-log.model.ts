/**
 * Mirrors the backend's real audit-log DTO exactly:
 * com.eventflow.ingestion.dto.DashboardLogEntry — { id, user, channel,
 * status, time }, served by GET /api/dashboard/logs (DashboardController).
 *
 * There is no GET /api/v1/notifications list endpoint on the backend
 * (NotificationController only has POST /send and POST /send-test) — this
 * dashboard endpoint is the one real, working "list of past notification
 * attempts" API in the codebase, already backing the Dashboard page's own
 * recent-activity table. `id` there is formatted server-side as
 * "NOTIF-<row id>" (DashboardService#toLogEntry), so it looks exactly like
 * the mock IDs (NOTIF-1025 etc.) this replaces — real data, same shape.
 *
 * `recipient` here is renamed from the DTO's `user` field on the way in
 * (see NotificationService#getRecentLogs) purely so the existing
 * NotificationsComponent template — which already reads `n.recipient` —
 * needed no changes.
 *
 * `payloadSnippet` genuinely has no backend equivalent: NotificationLog
 * (the audit row this DTO is built from) only ever stores
 * eventId/recipientId/channel/status/createdAt — never the actual message
 * subject/body — so it's optional and intentionally left unset for every
 * real entry. NotificationsComponent's template already guards it with
 * *ngIf, so that detail panel section simply doesn't render for real data
 * instead of showing a fabricated value.
 */
export interface NotificationLogEntry {
  id: string;
  recipient: string;
  channel: string;
  status: string;
  time: string;
  payloadSnippet?: string;
}
