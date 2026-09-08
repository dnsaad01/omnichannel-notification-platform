package com.eventflow.ingestion.model;

import jakarta.persistence.Column;
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
 * A real, persisted DLQ entry — one row per record consumed off the
 * notification-dlq Kafka topic (see DlqMessageConsumer). Backs the
 * Monitoring page's DLQ Inspection Modal via DlqManagementService /
 * MonitoringController's /api/monitoring/dlq endpoints, replacing what used
 * to be a hardcoded array (MonitoringComponent.dlqMessages) that silently
 * reset itself to the same 3 fake entries on every page refresh.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dlq_messages")
public class DlqMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String eventId;
  private String recipient;
  private String channel;

  @Column(length = 2000)
  private String errorReason;

  /** Topic the record was originally destined for before
   *  DeadLetterPublishingRecoverer (KafkaConsumerConfig) rerouted it to
   *  notification-dlq — captured from the DLT_ORIGINAL_TOPIC header spring-
   *  kafka adds automatically. Used by DlqManagementService#replayAll to
   *  republish to the right place instead of guessing. */
  private String originalTopic;

  /** JSON snapshot of the original NotificationEvent payload, so a replay
   *  can reconstruct and republish the exact same event. */
  @Column(columnDefinition = "TEXT")
  private String payload;

  private LocalDateTime createdAt;
}
