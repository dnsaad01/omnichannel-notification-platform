package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dto.DashboardLogEntry;
import com.eventflow.ingestion.dto.DashboardStatsResponse;
import com.eventflow.ingestion.dto.PagedLogsResponse;
import com.eventflow.ingestion.model.NotificationLog;
import com.eventflow.ingestion.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DashboardService {

  /** Upper bound on page size for GET /api/dashboard/logs/page — a caller
   *  passing an unreasonably large size (or 0/negative) is clamped instead
   *  of either crashing PageRequest.of() (which throws below 1) or letting
   *  a single request pull the entire notification_logs table. */
  private static final int MAX_PAGE_SIZE = 200;

  private final NotificationLogRepository notificationLogRepository;

  public DashboardStatsResponse getStats() {
    long total = notificationLogRepository.count();
    long delivered = notificationLogRepository.countByStatus(NotificationLogService.STATUS_DELIVERED);
    long activeChannels = notificationLogRepository.findDistinctChannels().size();

    double successRate = total > 0 ? (delivered * 100.0 / total) : 0.0;

    return DashboardStatsResponse.builder()
      .totalSent(String.format(Locale.US, "%,d", total))
      .successRate(String.format(Locale.US, "%.1f%%", successRate))
      .activeChannels(String.valueOf(activeChannels))
      .build();
  }

  /** Backs the Dashboard page's small recent-activity widget
   *  (dashboard.service.ts#getRecentLogs) — a flat, capped list with no
   *  pagination metadata. Left untouched by the Notifications page's
   *  pagination fix; see getRecentLogsPage for that. */
  public List<DashboardLogEntry> getRecentLogs(int limit) {
    return notificationLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
      .stream()
      .map(this::toLogEntry)
      .toList();
  }

  /**
   * Real pagination for the Notifications page's "Journal des envois"
   * table (GET /api/dashboard/logs/page). Unlike getRecentLogs above —
   * which only ever fetches one capped page starting at 0 — this reads an
   * arbitrary page/size and returns totalElements/totalPages alongside the
   * rows, which is what NotificationsComponent needs to render real
   * Précédent/Suivant controls and know when it has reached either end,
   * instead of being stuck on a single hardcoded "Tous (50)" slice while
   * the table actually holds 300+ rows.
   *
   * page is clamped to >= 0 and size to [1, MAX_PAGE_SIZE] rather than
   * letting an out-of-range value either throw (PageRequest.of rejects a
   * size < 1) or silently return an empty/huge page.
   */
  public PagedLogsResponse getRecentLogsPage(int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

    Page<NotificationLog> result = notificationLogRepository
      .findAllOrderedByCreatedAtDesc(PageRequest.of(safePage, safeSize));

    List<DashboardLogEntry> content = result.getContent().stream()
      .map(this::toLogEntry)
      .toList();

    return PagedLogsResponse.builder()
      .content(content)
      .page(safePage)
      .size(safeSize)
      .totalElements(result.getTotalElements())
      .totalPages(result.getTotalPages())
      .build();
  }

  private DashboardLogEntry toLogEntry(NotificationLog logRow) {
    return DashboardLogEntry.builder()
      .id("NOTIF-" + logRow.getId())
      .user(logRow.getRecipientId())
      .channel(logRow.getChannel())
      .status(toDisplayStatus(logRow.getStatus()))
      .time(toRelativeTime(logRow.getCreatedAt()))
      .build();
  }

  private String toDisplayStatus(String status) {
    if (status == null) {
      return "Unknown";
    }
    return switch (status) {
      case NotificationLogService.STATUS_DELIVERED -> "Delivered";
      case NotificationLogService.STATUS_FAILED -> "Failed";
      case NotificationLogService.STATUS_SUPPRESSED -> "Suppressed";
      default -> status;
    };
  }

  private String toRelativeTime(LocalDateTime createdAt) {
    if (createdAt == null) {
      return "unknown";
    }
    long minutes = Duration.between(createdAt, LocalDateTime.now()).toMinutes();
    if (minutes < 1) {
      return "just now";
    }
    if (minutes < 60) {
      return minutes + " min ago";
    }
    long hours = minutes / 60;
    if (hours < 24) {
      return hours + "h ago";
    }
    return (hours / 24) + "d ago";
  }
}
