package com.eventflow.ingestion.service;

import com.eventflow.ingestion.model.NotificationLog;
import com.eventflow.ingestion.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Records one audit row per channel-consumer delivery attempt. Backs the
 * /api/dashboard/* endpoints with real counts instead of hardcoded numbers.
 * Failures here are logged and swallowed — an audit-trail write must never
 * take down the actual notification delivery path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationLogService {

  public static final String STATUS_DELIVERED = "DELIVERED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_SUPPRESSED = "SUPPRESSED";

  private final NotificationLogRepository notificationLogRepository;

  public void record(String eventId, String recipientId, String channel, String status) {
    try {
      notificationLogRepository.save(NotificationLog.builder()
        .eventId(eventId)
        .recipientId(recipientId)
        .channel(channel)
        .status(status)
        .createdAt(LocalDateTime.now())
        .build());
    } catch (Exception e) {
      log.warn("Failed to persist notification audit log for event [{}]: {}", eventId, e.getMessage());
    }
  }
}
