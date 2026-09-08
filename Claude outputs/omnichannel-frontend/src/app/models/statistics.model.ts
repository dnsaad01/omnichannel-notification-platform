/**
 * Mirrors the backend's real DTOs exactly:
 * com.eventflow.ingestion.dto.StatisticsResponse / ChannelStatistics,
 * served by GET /api/dashboard/statistics (StatisticsController).
 *
 * `clickRate` is always the literal string "Non suivi" ("not tracked") —
 * nothing in this codebase tracks link clicks anywhere (no redirect/click
 * pixel exists, unlike the real email-open pixel), so the backend
 * deliberately never fabricates a percentage for it. `openRate` IS real,
 * but scoped to workflow-triggered emails only — see StatisticsService's
 * class doc comment on the backend for the full explanation of why it
 * doesn't cover every EMAIL row in the channel breakdown below it.
 */
export interface ChannelStatistics {
  name: string;
  sent: string;
  delivered: string;
  rate: string;
}

export interface StatisticsResponse {
  totalSent: string;
  deliveryRate: string;
  openRate: string;
  clickRate: string;
  channels: ChannelStatistics[];
}
