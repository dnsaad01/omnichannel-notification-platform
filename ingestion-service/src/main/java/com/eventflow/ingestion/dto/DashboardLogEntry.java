package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shape matches Angular's dashboard.component.ts recentLogs entries exactly:
 * { id, user, channel, status, time }.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLogEntry {
    private String id;
    private String user;
    private String channel;
    private String status;
    private String time;
}
