package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of StatisticsResponse#channels — shape matches Angular's
 * statistics.component.ts's existing `channels` array exactly:
 * { name, sent, delivered, rate }, so the frontend template needed no
 * changes beyond wiring in the real data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelStatistics {
  private String name;
  private String sent;
  private String delivered;
  private String rate;
}
