package com.eventflow.ingestion.consumer;

import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.model.DlqMessage;
import com.eventflow.ingestion.repository.DlqMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Proves DlqMessageConsumer actually persists what it's handed into
 * dlq_messages correctly and never throws — the backend half of "je veux
 * m'assurer que le consumer Kafka DlqMessageConsumer... alimente
 * correctement et de façon stable la table PostgreSQL dlq_messages". Before
 * this test existed, nothing pinned down that consume() extracts the DLT_*
 * headers correctly, infers the channel correctly, or — critically — never
 * lets an exception escape (which would otherwise dead-letter the record
 * right back onto notification-dlq, per this class's own doc comment,
 * looping forever on a poison message).
 */
@ExtendWith(MockitoExtension.class)
class DlqMessageConsumerTest {

  @Mock
  private DlqMessageRepository dlqMessageRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  private DlqMessageConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new DlqMessageConsumer(dlqMessageRepository, objectMapper);
  }

  private ConsumerRecord<String, NotificationEvent> aRecord(
      NotificationEvent event, String key, String originalTopic, String exceptionMessage, String exceptionFqcn) {
    ConsumerRecord<String, NotificationEvent> record =
      new ConsumerRecord<>("notification-dlq", 0, 0L, key, event);
    if (originalTopic != null) {
      record.headers().add(new RecordHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC, originalTopic.getBytes(StandardCharsets.UTF_8)));
    }
    if (exceptionMessage != null) {
      record.headers().add(new RecordHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE, exceptionMessage.getBytes(StandardCharsets.UTF_8)));
    }
    if (exceptionFqcn != null) {
      record.headers().add(new RecordHeader(KafkaHeaders.DLT_EXCEPTION_FQCN, exceptionFqcn.getBytes(StandardCharsets.UTF_8)));
    }
    return record;
  }

  @Test
  void consumeShouldPersistAllExtractedFieldsForAnEmailFailure() throws Exception {
    NotificationEvent event = NotificationEvent.builder().eventId("evt-1").recipientId("user@test.com").build();
    ConsumerRecord<String, NotificationEvent> record =
      aRecord(event, "user@test.com", "notification.email.high", "SMTP timeout", null);

    consumer.consume(record);

    ArgumentCaptor<DlqMessage> captor = ArgumentCaptor.forClass(DlqMessage.class);
    verify(dlqMessageRepository).save(captor.capture());

    DlqMessage saved = captor.getValue();
    assertEquals("evt-1", saved.getEventId());
    assertEquals("user@test.com", saved.getRecipient());
    assertEquals("EMAIL", saved.getChannel());
    assertEquals("SMTP timeout", saved.getErrorReason());
    assertEquals("notification.email.high", saved.getOriginalTopic());
    assertEquals(objectMapper.writeValueAsString(event), saved.getPayload());
  }

  @Test
  void consumeShouldInferSmsAndPushAndFallBackToUnknownForAnUnrecognizedTopic() {
    NotificationEvent event = NotificationEvent.builder().eventId("evt-2").recipientId("+212600000000").build();

    consumer.consume(aRecord(event, "+212600000000", "notification.sms.low", "Twilio error", null));
    consumer.consume(aRecord(event, "device-token", "notification.push.high", "FCM error", null));
    consumer.consume(aRecord(event, "device-token", "notification.webhook", "boom", null));
    consumer.consume(aRecord(event, "device-token", null, "boom", null));

    ArgumentCaptor<DlqMessage> captor = ArgumentCaptor.forClass(DlqMessage.class);
    verify(dlqMessageRepository, org.mockito.Mockito.times(4)).save(captor.capture());

    assertEquals("SMS", captor.getAllValues().get(0).getChannel());
    assertEquals("PUSH", captor.getAllValues().get(1).getChannel());
    assertEquals("UNKNOWN", captor.getAllValues().get(2).getChannel());
    assertEquals("UNKNOWN", captor.getAllValues().get(3).getChannel());
  }

  @Test
  void consumeShouldFallBackToTheExceptionFqcnWhenNoExceptionMessageHeaderIsPresent() {
    NotificationEvent event = NotificationEvent.builder().eventId("evt-3").recipientId("user@test.com").build();

    consumer.consume(aRecord(event, "user@test.com", "notification.email.low", null, "java.lang.RuntimeException"));

    ArgumentCaptor<DlqMessage> captor = ArgumentCaptor.forClass(DlqMessage.class);
    verify(dlqMessageRepository).save(captor.capture());
    assertEquals("java.lang.RuntimeException", captor.getValue().getErrorReason());
  }

  @Test
  void consumeShouldDefaultToAGenericReasonWhenNeitherExceptionHeaderIsPresent() {
    NotificationEvent event = NotificationEvent.builder().eventId("evt-4").recipientId("user@test.com").build();

    consumer.consume(aRecord(event, "user@test.com", "notification.email.low", null, null));

    ArgumentCaptor<DlqMessage> captor = ArgumentCaptor.forClass(DlqMessage.class);
    verify(dlqMessageRepository).save(captor.capture());
    assertEquals("Unknown error", captor.getValue().getErrorReason());
  }

  @Test
  void consumeShouldFallBackToTheRecordKeyAsRecipientWhenTheEventValueIsNull() {
    consumer.consume(aRecord(null, "raw-key", "notification.email.low", "boom", null));

    ArgumentCaptor<DlqMessage> captor = ArgumentCaptor.forClass(DlqMessage.class);
    verify(dlqMessageRepository).save(captor.capture());

    DlqMessage saved = captor.getValue();
    assertEquals("raw-key", saved.getRecipient());
    assertEquals(null, saved.getEventId());
    assertEquals(null, saved.getPayload());
  }

  @Test
  void consumeShouldNeverThrowEvenWhenTheRepositoryFails() {
    NotificationEvent event = NotificationEvent.builder().eventId("evt-5").recipientId("user@test.com").build();
    ConsumerRecord<String, NotificationEvent> record =
      aRecord(event, "user@test.com", "notification.email.low", "boom", null);

    doThrow(new RuntimeException("DB down")).when(dlqMessageRepository).save(any(DlqMessage.class));

    // Must NOT propagate: an uncaught exception here would be treated as a
    // listener failure by the shared error handler and dead-letter this
    // record right back onto notification-dlq — an infinite loop.
    assertDoesNotThrow(() -> consumer.consume(record));
  }
}
