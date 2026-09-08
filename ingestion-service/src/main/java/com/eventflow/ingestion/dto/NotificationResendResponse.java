package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Confirmation payload for POST /api/dashboard/logs/{id}/resend. Does NOT
 * carry a new notification_logs row — that row is written asynchronously,
 * later, by whichever channel consumer (EmailNotificationConsumer/etc.)
 * picks the republished event off Kafka. This response only confirms the
 * republish itself succeeded; NotificationsComponent re-fetches the log
 * list afterwards to pick up the new row once it exists (see that
 * component's resendNotification()).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResendResponse {
  private String originalId;
  private String recipient;
  private String channel;
  private String message;
}
