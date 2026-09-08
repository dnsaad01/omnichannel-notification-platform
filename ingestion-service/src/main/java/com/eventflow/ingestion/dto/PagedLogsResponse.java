package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response shape for GET /api/dashboard/logs/page — the real, paginated
 * counterpart to GET /api/dashboard/logs (which stays a flat array and is
 * left untouched: it still backs the Dashboard page's own small
 * recent-activity widget, via services/dashboard.service.ts, and changing
 * its shape would have broken that widget and its existing tests for no
 * benefit).
 *
 * This is what the Notifications page's "Journal des envois" table
 * actually needs: not just the current page's rows (content), but enough
 * metadata (totalElements/totalPages) to render real Pagination Controls
 * and know when "Suivant" should be disabled — see
 * NotificationsComponent#goToNextPage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedLogsResponse {
  private List<DashboardLogEntry> content;
  private int page;
  private int size;
  private long totalElements;
  private int totalPages;
}
