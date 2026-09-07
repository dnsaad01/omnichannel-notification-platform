package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dto.ChannelStatistics;
import com.eventflow.ingestion.dto.StatisticsResponse;
import com.eventflow.ingestion.repository.NotificationLogRepository;
import com.eventflow.ingestion.workflow.repository.WorkflowExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Real aggregate metrics for the Statistics page (StatisticsController,
 * GET /api/dashboard/statistics) — computed from TWO independent data
 * sources that don't cover the same population of sends. Read this class
 * fully before trusting a number at face value:
 *
 *  - totalSent / deliveryRate / channel breakdown: notification_logs, the
 *    same audit table DashboardService already aggregates globally — one
 *    row per channel-consumer delivery attempt (Email/SMS/Push), whichever
 *    path triggered it (direct /api/v1/notifications/send OR the Workflow
 *    Engine's NOTIFICATION node).
 *  - openRate: workflow_executions' context_json, and ONLY covers emails
 *    the Workflow Engine sent — see computeOpenRate's own doc comment.
 *  - clickRate: not computed at all. Nothing in this codebase tracks link
 *    clicks — no redirect/click-pixel exists anywhere, unlike the real
 *    email-open pixel (TrackingController). Returning a fabricated
 *    percentage here would misrepresent data that doesn't exist, so this
 *    is always the literal string "Non suivi" rather than a number.
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

  private static final String NOT_TRACKED = "Non suivi";

  private final NotificationLogRepository notificationLogRepository;
  private final WorkflowExecutionRepository workflowExecutionRepository;

  public StatisticsResponse getStatistics() {
    long totalSent = notificationLogRepository.count();
    long totalDelivered = notificationLogRepository.countByStatus(NotificationLogService.STATUS_DELIVERED);

    return StatisticsResponse.builder()
      .totalSent(formatCount(totalSent))
      .deliveryRate(formatRate(totalDelivered, totalSent))
      .openRate(computeOpenRate())
      .clickRate(NOT_TRACKED)
      .channels(computeChannelBreakdown())
      .build();
  }

  /**
   * Email open rate — the only real "engagement" signal this codebase
   * tracks anywhere (EmailTrackingService / TrackingController's 1x1 pixel).
   * Even that is scoped narrowly: the pixel is appended to an EMAIL body
   * only by NotificationNodeHandler#dispatchEmailSynchronously (the
   * Workflow Engine's EMAIL dispatch path) — never by the direct
   * /api/v1/notifications/send → EmailNotificationConsumer path, which has
   * no tracking pixel logic at all. So this percentage is "opened ÷ sent"
   * among workflow-triggered emails specifically, not among every EMAIL row
   * counted in the channel breakdown below. That's a real, disclosed scope
   * mismatch — not a bug — because there is genuinely no open-tracking
   * signal for the other path to include.
   */
  private String computeOpenRate() {
    long emailSent = workflowExecutionRepository.countEmailSent();
    long emailOpened = workflowExecutionRepository.countEmailOpened();
    return formatRate(emailOpened, emailSent);
  }

  /** Per-channel sent/delivered breakdown, straight off notification_logs —
   *  same table/statuses DashboardService already aggregates globally, just
   *  grouped by channel here via one count-pair per distinct channel. */
  private List<ChannelStatistics> computeChannelBreakdown() {
    return notificationLogRepository.findDistinctChannels().stream()
      .map(channel -> {
        long sent = notificationLogRepository.countByChannel(channel);
        long delivered = notificationLogRepository.countByChannelAndStatus(channel, NotificationLogService.STATUS_DELIVERED);
        return ChannelStatistics.builder()
          .name(toDisplayChannelName(channel))
          .sent(formatCount(sent))
          .delivered(formatCount(delivered))
          .rate(formatRate(delivered, sent))
          .build();
      })
      .toList();
  }

  private String toDisplayChannelName(String channel) {
    if (channel == null) {
      return "Unknown";
    }
    return switch (channel.toUpperCase(Locale.ROOT)) {
      case "EMAIL" -> "Email";
      case "SMS" -> "SMS";
      case "PUSH" -> "Push";
      default -> channel;
    };
  }

  private String formatCount(long value) {
    return String.format(Locale.US, "%,d", value);
  }

  private String formatRate(long numerator, long denominator) {
    double rate = denominator > 0 ? (numerator * 100.0 / denominator) : 0.0;
    return String.format(Locale.US, "%.1f%%", rate);
  }
}
