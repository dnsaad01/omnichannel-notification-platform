package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Shape matches Angular's statistics.component.ts's `globalStats` +
 * `channels` exactly (StatisticsController, GET /api/dashboard/statistics),
 * pre-formatted strings same as DashboardStatsResponse so the existing
 * template needs no changes.
 *
 * clickRate is always the literal string "Non suivi" ("not tracked") — see
 * StatisticsService's class doc comment for why: nothing in this codebase
 * tracks link clicks anywhere (no click-tracking redirect/pixel exists at
 * all, unlike email opens), so returning a fabricated percentage here would
 * be dishonest rather than a real metric. openRate IS real, but scoped to
 * workflow-triggered emails only — see StatisticsService#computeOpenRate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsResponse {
  private String totalSent;
  private String deliveryRate;
  private String openRate;
  private String clickRate;
  private List<ChannelStatistics> channels;
}
