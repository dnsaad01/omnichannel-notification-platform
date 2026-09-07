package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shape matches what the Angular dashboard already renders directly
 * (stats.totalSent / stats.successRate / stats.activeChannels) — kept as
 * pre-formatted strings so the existing template needs no changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private String totalSent;
    private String successRate;
    private String activeChannels;
}
