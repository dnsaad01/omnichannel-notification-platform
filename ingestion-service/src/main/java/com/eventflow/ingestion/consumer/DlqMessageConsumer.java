package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.DlqMessage;
import com.eventflow.ingestion.repository.DlqMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Persists every record that lands on notification-dlq into the
 * dlq_messages table, so the Monitoring page's DLQ inspector has a real,
 * queryable, deletable store instead of the hardcoded array that used to
 * live in MonitoringComponent.
 *
 * ⚠️ Root cause of the reported bug ("deleted DLQ messages reappear on
 * refresh / modal reopen"): nothing ever consumed notification-dlq before
 * this class existed, and the frontend never called a backend endpoint at
 * all — it just filtered a component-local array (MonitoringComponent
 * .dlqMessages) that got reset to the same 3 hardcoded entries on every new
 * component instance (page refresh, or the host component being recreated).
 * Now the topic is drained into Postgres by a dedicated consumer group, and
 * DELETE /api/monitoring/dlq/{id} removes the row for good — there is no
 * "re-fetch from Kafka" step left that could resurrect it.
 *
 * Dedicated consumer group (notification-dlq-inspector-group), separate
 * from notification-ingestion-group used by the channel consumers, so:
 *  (a) it doesn't compete with them for partitions/rebalances, and
 *  (b) being a brand-new group with global auto-offset-reset=earliest
 *      (application.properties), its first run naturally backfills whatever
 *      was already sitting in notification-dlq before this fix shipped,
 *      instead of only seeing messages published from now on.
 *
 * Letting consume() return normally is what commits the offset (Spring
 * Kafka's default BATCH ack mode, same as every other consumer in this
 * project — no manual Acknowledgment needed). That commit is what actually
 * stops a message from reappearing after a backend restart: it has been
 * durably consumed, not just fetched-and-displayed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqMessageConsumer {

  private final DlqMessageRepository dlqMessageRepository;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = KafkaTopicConfig.TOPIC_DLQ, groupId = "notification-dlq-inspector-group")
  public void consume(ConsumerRecord<String, NotificationEvent> record) {
    try {
      NotificationEvent event = record.value();
      String originalTopic = header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
      String exceptionMessage = header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
      String exceptionFqcn = header(record, KafkaHeaders.DLT_EXCEPTION_FQCN);

      String errorReason = exceptionMessage != null
        ? exceptionMessage
        : (exceptionFqcn != null ? exceptionFqcn : "Unknown error");

      dlqMessageRepository.save(DlqMessage.builder()
        .eventId(event != null ? event.getEventId() : null)
        .recipient(event != null ? event.getRecipientId() : record.key())
        .channel(inferChannel(originalTopic))
        .errorReason(errorReason)
        .originalTopic(originalTopic)
        .payload(event != null ? objectMapper.writeValueAsString(event) : null)
        .createdAt(LocalDateTime.now())
        .build());

      log.info("DLQ message persisted [eventId={}, originalTopic={}, reason={}]",
        event != null ? event.getEventId() : "unknown", originalTopic, errorReason);
    } catch (Exception e) {
      // Swallow-and-log, same pattern as NotificationLogService#record: a
      // failure to persist this audit row must NOT rethrow, because the
      // shared CommonErrorHandler bean (KafkaConsumerConfig) would treat it
      // like any other listener failure and dead-letter the record right
      // back onto notification-dlq — an infinite loop for a poison message.
      log.error("Failed to persist DLQ message from topic [{}]: {}", record.topic(), e.getMessage(), e);
    }
  }

  private String inferChannel(String originalTopic) {
    if (originalTopic == null) return "UNKNOWN";
    if (originalTopic.contains("email")) return "EMAIL";
    if (originalTopic.contains("sms")) return "SMS";
    if (originalTopic.contains("push")) return "PUSH";
    return "UNKNOWN";
  }

  private String header(ConsumerRecord<?, ?> record, String key) {
    Header header = record.headers().lastHeader(key);
    return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
  }
}
