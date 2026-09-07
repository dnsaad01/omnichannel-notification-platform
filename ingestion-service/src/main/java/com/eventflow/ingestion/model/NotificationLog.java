package com.eventflow.ingestion.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A lightweight delivery-attempt audit row, written once per channel consumer
 * invocation (see EmailNotificationConsumer / SmsNotificationConsumer /
 * PushNotificationConsumer). Backs the real /api/dashboard/* endpoints so the
 * frontend dashboard reflects actual traffic instead of hardcoded fallback data.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notification_logs")
public class NotificationLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String eventId;
  private String recipientId;
  private String channel;

  /** DELIVERED, FAILED, or SUPPRESSED (blocked by preferences / quiet hours). */
  private String status;

  private LocalDateTime createdAt;
}
