package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dto.DashboardLogEntry;
import com.eventflow.ingestion.dto.DashboardStatsResponse;
import com.eventflow.ingestion.model.NotificationLog;
import com.eventflow.ingestion.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DashboardService {

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

  public List<DashboardLogEntry> getRecentLogs(int limit) {
    return notificationLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
      .stream()
      .map(this::toLogEntry)
      .toList();
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
