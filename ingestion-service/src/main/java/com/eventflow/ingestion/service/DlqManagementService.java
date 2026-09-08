package com.eventflow.ingestion.service;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.DlqMessageResponse;
import com.eventflow.ingestion.dto.DlqReplayResponse;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.exception.DlqMessageNotFoundException;
import com.eventflow.ingestion.model.DlqMessage;
import com.eventflow.ingestion.repository.DlqMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Real backing for the DLQ Inspection Modal (MonitoringController's /dlq
 * endpoints), reading and mutating the dlq_messages table that
 * DlqMessageConsumer populates from the real notification-dlq Kafka topic.
 *
 * This is what makes "Supprimer" and "Rejouer Tous les Messages" actually
 * stick across a refresh: both previously only mutated a component-local
 * array on the frontend (MonitoringComponent.dlqMessages) that was never
 * backed by anything, so a fresh page load or modal reopen always
 * re-created the same 3 hardcoded entries. There is no Kafka-side
 * "un-consume" / random-access delete operation — a log-based topic simply
 * doesn't support that — so persistence happens once, at consume time
 * (DlqMessageConsumer), and everything below operates on that durable copy
 * instead of re-reading Kafka on every request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqManagementService {

  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final DlqMessageRepository dlqMessageRepository;
  private final KafkaTemplate<Object, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public List<DlqMessageResponse> listMessages() {
    return dlqMessageRepository.findAllByOrderByCreatedAtDesc().stream()
      .map(this::toResponse)
      .toList();
  }

  /** Deletes the row for good — nothing subsequently re-reads Kafka to
   *  rebuild the list, so this id cannot come back on the next GET /dlq. */
  @Transactional
  public void deleteMessage(Long id) {
    if (!dlqMessageRepository.existsById(id)) {
      throw new DlqMessageNotFoundException(id);
    }
    dlqMessageRepository.deleteById(id);
  }

  /**
   * Republishes every stored DLQ message onto the topic it originally
   * failed on (captured at consume time from the DLT_ORIGINAL_TOPIC header
   * — see DlqMessageConsumer), then removes it from the table. A message
   * whose original topic wasn't captured (e.g. a row saved before this fix
   * shipped) falls back to the ingestion topic so it re-enters the normal
   * routing pipeline rather than being silently dropped.
   *
   * A message that fails to republish is left in the table rather than
   * deleted, so a Kafka broker hiccup during replay doesn't quietly lose
   * the record — it just stays visible for a retry.
   */
  @Transactional
  public DlqReplayResponse replayAll() {
    List<DlqMessage> messages = dlqMessageRepository.findAll();
    int replayedCount = 0;

    for (DlqMessage message : messages) {
      String targetTopic = message.getOriginalTopic() != null
        ? message.getOriginalTopic()
        : KafkaTopicConfig.TOPIC_INGESTION;

      try {
        NotificationEvent payload = message.getPayload() != null
          ? objectMapper.readValue(message.getPayload(), NotificationEvent.class)
          : null;
        kafkaTemplate.send(targetTopic, message.getRecipient(), payload);
        dlqMessageRepository.delete(message);
        replayedCount++;
      } catch (Exception e) {
        log.error("Failed to replay DLQ message [id={}] onto topic [{}]: {}",
          message.getId(), targetTopic, e.getMessage());
      }
    }

    return DlqReplayResponse.builder().replayedCount(replayedCount).build();
  }

  private DlqMessageResponse toResponse(DlqMessage message) {
    return DlqMessageResponse.builder()
      .id(String.valueOf(message.getId()))
      .recipient(message.getRecipient())
      .channel(message.getChannel())
      .errorReason(message.getErrorReason())
      .timestamp(message.getCreatedAt() != null ? message.getCreatedAt().format(TIMESTAMP_FORMAT) : "")
      .build();
  }
}
