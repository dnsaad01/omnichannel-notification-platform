package com.eventflow.ingestion.service;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.DlqMessageResponse;
import com.eventflow.ingestion.dto.DlqReplayResponse;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.exception.DlqMessageNotFoundException;
import com.eventflow.ingestion.model.DlqMessage;
import com.eventflow.ingestion.repository.DlqMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;

/**
 * Regression coverage for the reported bug: a "deleted" or "replayed" DLQ
 * message must not be able to reappear on the next listMessages() call.
 * Before this class existed, the DLQ inspector had no backing store at all
 * — MonitoringComponent kept a hardcoded array on the frontend, so there
 * was nothing here to test in the first place.
 */
@ExtendWith(MockitoExtension.class)
class DlqManagementServiceTest {

  @Mock
  private DlqMessageRepository dlqMessageRepository;

  @Mock
  private KafkaTemplate<Object, Object> kafkaTemplate;

  private ObjectMapper objectMapper;
  private DlqManagementService dlqManagementService;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();
    dlqManagementService = new DlqManagementService(dlqMessageRepository, kafkaTemplate, objectMapper);
  }

  @Test
  void listMessagesShouldMapEntitiesToResponses() {
    DlqMessage message = DlqMessage.builder()
      .id(1L)
      .recipient("user-1")
      .channel("EMAIL")
      .errorReason("SMTP 550 Invalid Recipient")
      .createdAt(LocalDateTime.of(2026, 9, 8, 10, 14, 22))
      .build();
    when(dlqMessageRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(message));

    List<DlqMessageResponse> result = dlqManagementService.listMessages();

    assertEquals(1, result.size());
    assertEquals("1", result.get(0).getId());
    assertEquals("user-1", result.get(0).getRecipient());
    assertEquals("10:14:22", result.get(0).getTimestamp());
  }

  @Test
  void deleteMessageShouldRemoveTheRowSoItCannotReappearOnTheNextList() {
    when(dlqMessageRepository.existsById(1L)).thenReturn(true);

    dlqManagementService.deleteMessage(1L);

    verify(dlqMessageRepository).deleteById(1L);
  }

  @Test
  void deleteMessageShouldThrowWhenTheIdDoesNotExistInsteadOfSilentlyNoOpping() {
    when(dlqMessageRepository.existsById(999L)).thenReturn(false);

    assertThrows(DlqMessageNotFoundException.class, () -> dlqManagementService.deleteMessage(999L));
    verify(dlqMessageRepository, never()).deleteById(any());
  }

  @Test
  void replayAllShouldRepublishEachMessageToItsOriginalTopicAndRemoveItFromTheStore() throws Exception {
    DlqMessage message = DlqMessage.builder()
      .id(1L)
      .recipient("user-1")
      .originalTopic(KafkaTopicConfig.TOPIC_EMAIL_HIGH)
      .payload(objectMapper.writeValueAsString(
        NotificationEvent.builder().eventId("evt-1").recipientId("user-1").build()))
      .build();
    when(dlqMessageRepository.findAll()).thenReturn(List.of(message));

    DlqReplayResponse response = dlqManagementService.replayAll();

    assertEquals(1, response.getReplayedCount());
    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_EMAIL_HIGH), eq("user-1"), any());
    verify(dlqMessageRepository).delete(message);
  }

  @Test
  void replayAllShouldFallBackToTheIngestionTopicWhenOriginalTopicWasNotCaptured() throws Exception {
    DlqMessage message = DlqMessage.builder()
      .id(2L)
      .recipient("user-2")
      .originalTopic(null)
      .payload(objectMapper.writeValueAsString(
        NotificationEvent.builder().eventId("evt-2").recipientId("user-2").build()))
      .build();
    when(dlqMessageRepository.findAll()).thenReturn(List.of(message));

    dlqManagementService.replayAll();

    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_INGESTION), eq("user-2"), any());
  }

  @Test
  void replayAllShouldLeaveAMessageInPlaceWhenRepublishingFailsInsteadOfLosingIt() {
    DlqMessage message = DlqMessage.builder()
      .id(3L)
      .recipient("user-3")
      .originalTopic(KafkaTopicConfig.TOPIC_SMS_HIGH)
      .payload("{ not valid json")
      .build();
    when(dlqMessageRepository.findAll()).thenReturn(List.of(message));

    DlqReplayResponse response = dlqManagementService.replayAll();

    assertEquals(0, response.getReplayedCount());
    verify(dlqMessageRepository, never()).delete(any());
  }
}
