package com.eventflow.ingestion.exception;

/**
 * Thrown by NotificationResendService when the log id given to
 * POST /api/dashboard/logs/{id}/resend doesn't match any row in
 * notification_logs — either because the numeric id doesn't exist, or
 * because the given id string couldn't be parsed at all (e.g. malformed
 * input rather than the expected "NOTIF-123" / "123" shape).
 */
public class NotificationLogNotFoundException extends RuntimeException {
  public NotificationLogNotFoundException(String logId) {
    super("Notification log not found: " + logId);
  }
}
