package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dto.NotificationResendResponse;
import com.eventflow.ingestion.exception.NotificationLogNotFoundException;
import com.eventflow.ingestion.model.NotificationLog;
import com.eventflow.ingestion.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Real backing for the Notifications page's "Relancer la notification"
 * button (DashboardController's POST /logs/{id}/resend).
 *
 * ⚠️ Root cause of the reported bug ("le toast vert disparaît au refresh,
 * la liste ne se met jamais à jour"): resendNotification() on the frontend
 * never called the backend at all — it just fabricated a fake
 * NotificationLogEntry client-side and spliced it into the component's
 * in-memory array (see the removed doc comment on
 * NotificationsComponent.resendNotification). Nothing was ever persisted,
 * so the injected row silently vanished on the very next 5s poll (or any
 * refresh), which is exactly the symptom reported. This service makes the
 * relance real: it republishes an event onto the same Kafka topic the
 * original notification would have used, so the normal channel-consumer
 * pipeline picks it up and writes a genuine new notification_logs row —
 * the same row NotificationsComponent re-fetches and displays.
 *
 * notification_logs intentionally does not retain the original subject/
 * body/payload (see NotificationLog's doc comment) — it's a lightweight
 * delivery-attempt audit row, not a full copy of the request. A resend
 * therefore republishes with a generic subject/body referencing the
 * original event id rather than reconstructing the exact original content;
 * see NotificationIngestionService#publishResend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationResendService {

  private static final String LOG_ID_PREFIX = "NOTIF-";

  private final NotificationLogRepository notificationLogRepository;
  private final NotificationIngestionService notificationIngestionService;

  public NotificationResendResponse resend(String logId) {
    Long id = parseId(logId);
    NotificationLog original = notificationLogRepository.findById(id)
      .orElseThrow(() -> new NotificationLogNotFoundException(logId));

    notificationIngestionService.publishResend(
      original.getRecipientId(), original.getChannel(), original.getEventId());

    log.info("Resent notification log [{}] (originalEventId={}) for recipient [{}] on channel [{}]",
      logId, original.getEventId(), original.getRecipientId(), original.getChannel());

    return NotificationResendResponse.builder()
      .originalId(logId)
      .recipient(original.getRecipientId())
      .channel(original.getChannel())
      .message("Notification relancée avec succès — un nouveau log apparaîtra sous peu.")
      .build();
  }

  /** Accepts both the display-formatted "NOTIF-123" id the frontend already
   *  has on every row (DashboardLogEntry#id) and a bare numeric id, so the
   *  frontend doesn't need to strip the prefix itself. Anything else — a
   *  missing id, or a non-numeric remainder — is treated as "not found"
   *  rather than a 400, mirroring DlqManagementService's not-found handling
   *  for an unknown id. */
  private Long parseId(String logId) {
    if (logId == null || logId.isBlank()) {
      throw new NotificationLogNotFoundException(logId);
    }
    String numeric = logId.startsWith(LOG_ID_PREFIX) ? logId.substring(LOG_ID_PREFIX.length()) : logId;
    try {
      return Long.parseLong(numeric.trim());
    } catch (NumberFormatException e) {
      throw new NotificationLogNotFoundException(logId);
    }
  }
}
