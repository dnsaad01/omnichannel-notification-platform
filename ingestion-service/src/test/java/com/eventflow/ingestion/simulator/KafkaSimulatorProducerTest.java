package com.eventflow.ingestion.simulator;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.dto.NotificationEvent;
import com.eventflow.ingestion.service.NotificationIngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Covers the Kafka Event Simulator's dispatch, batching, rate-clamping and
 * start/stop state machine. startSimulation/stopSimulation spin up (and
 * must always tear down) a real ScheduledExecutorService, so every test
 * that starts one is paired with an unconditional stopSimulation() in
 * @AfterEach — Executors.newSingleThreadScheduledExecutor() creates
 * non-daemon threads, and a leaked one would hang the whole test JVM, not
 * just this test class. kafkaTemplate is fully mocked (no real broker), so
 * a background tick firing during a test is harmless — it never reaches
 * the network — but its exact timing is still non-deterministic, so tests
 * only assert on state that's set synchronously on the calling thread
 * (isRunning / getStatus()'s "active" and "ratePerSecond" fields), never on
 * exact kafkaTemplate call counts once continuous simulation has started.
 */
@ExtendWith(MockitoExtension.class)
class KafkaSimulatorProducerTest {

  @Mock
  private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

  @Mock
  private NotificationIngestionService notificationIngestionService;

  private KafkaSimulatorProducer producer;

  @BeforeEach
  void setUp() {
    producer = new KafkaSimulatorProducer(kafkaTemplate, notificationIngestionService);
  }

  @AfterEach
  void tearDown() {
    producer.stopSimulation();
  }

  @Test
  void sendSingleSimulatedEventShouldPublishTheGivenCustomEventToBothTopics() {
    NotificationEvent custom = NotificationEvent.builder()
      .eventId("evt-1").recipientId("user@example.com").channel("EMAIL").priority("HIGH").build();

    NotificationEvent result = producer.sendSingleSimulatedEvent(custom);

    assertEquals(custom, result);
    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_INGESTION), eq("user@example.com"), eq(custom));
    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_EMAIL_HIGH), eq("user@example.com"), eq(custom));
    verify(kafkaTemplate, times(2)).send(any(), any(), any());
  }

  @Test
  void sendSingleSimulatedEventShouldRouteALowPrioritySmsEventToTheLowSmsTopic() {
    NotificationEvent custom = NotificationEvent.builder()
      .eventId("evt-2").recipientId("+15550001111").channel("SMS").priority("LOW").build();

    producer.sendSingleSimulatedEvent(custom);

    verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_SMS_LOW), eq("+15550001111"), eq(custom));
  }

  @Test
  void sendSingleSimulatedEventShouldGenerateARandomEventWhenGivenNull() {
    NotificationEvent result = producer.sendSingleSimulatedEvent(null);

    assertNotNull(result);
    assertTrue(result.getEventId().startsWith("SIM-"));
    assertNotNull(result.getRecipientId());
    verify(kafkaTemplate, times(2)).send(any(), any(), any());
  }

  @Test
  void sendSingleSimulatedEventShouldDefaultToARandomActiveChannelWhenTheCustomEventHasNoChannel() {
    // Reproduces the exact reported crash: the "Envoyer 1 Événement" button
    // POSTs `{}` (an empty JSON object, not an absent body) when the user
    // hasn't filled in a custom payload — SimulatorController deserializes
    // that into a NotificationEvent with every field null, channel
    // included. Before the fix, this threw a NullPointerException at
    // determineChannelTopic's channel.toLowerCase().
    NotificationEvent blankEvent = NotificationEvent.builder().build();

    NotificationEvent result = producer.sendSingleSimulatedEvent(blankEvent);

    assertNotNull(result.getChannel());
    assertTrue(Set.of("EMAIL", "SMS", "PUSH").contains(result.getChannel()),
      "expected the fallback channel to come from the active/routed channel pool");
    verify(kafkaTemplate, times(2)).send(any(), any(), any());
  }

  @Test
  void sendSingleSimulatedEventShouldDefaultToAnActiveChannelWhenTheCustomEventHasABlankChannel() {
    NotificationEvent blankChannel = NotificationEvent.builder()
      .eventId("evt-blank").recipientId("user@example.com").channel("   ").build();

    NotificationEvent result = producer.sendSingleSimulatedEvent(blankChannel);

    assertTrue(Set.of("EMAIL", "SMS", "PUSH").contains(result.getChannel()));
  }

  @Test
  void sendSingleSimulatedEventShouldNotOverrideAChannelTheCallerAlreadyProvided() {
    NotificationEvent custom = NotificationEvent.builder()
      .eventId("evt-4").recipientId("+15550001111").channel("SMS").priority("LOW").build();

    NotificationEvent result = producer.sendSingleSimulatedEvent(custom);

    assertEquals("SMS", result.getChannel());
  }

  @Test
  void sendSingleSimulatedEventShouldWrapAndRethrowWhenKafkaTemplateThrows() {
    NotificationEvent custom = NotificationEvent.builder()
      .eventId("evt-3").recipientId("user@example.com").channel("EMAIL").priority("HIGH").build();
    doThrow(new RuntimeException("broker unreachable")).when(kafkaTemplate).send(any(), any(), any());

    RuntimeException ex = assertThrows(RuntimeException.class, () -> producer.sendSingleSimulatedEvent(custom));

    assertEquals("Failed to publish simulated event", ex.getMessage());
    assertEquals("broker unreachable", ex.getCause().getMessage());
    assertEquals(1L, producer.getStatus().get("totalErrors"));
  }

  @Test
  void generateRandomEventShouldAlwaysPairAPhoneRecipientWithSmsOrWhatsappAndAnEmailRecipientOtherwise() {
    // channel selection is random, so this drives it many times and asserts
    // the invariant holds for whichever channel came up each time, rather
    // than asserting on one specific expected value.
    for (int i = 0; i < 50; i++) {
      NotificationEvent event = producer.generateRandomEvent();
      assertTrue(Set.of("EMAIL", "SMS", "PUSH", "WHATSAPP").contains(event.getChannel()));
      assertTrue(Set.of("HIGH", "LOW").contains(event.getPriority()));
      if ("SMS".equals(event.getChannel()) || "WHATSAPP".equals(event.getChannel())) {
        assertTrue(event.getRecipientId().startsWith("+"), "expected a phone recipient for " + event.getChannel());
      } else {
        assertTrue(event.getRecipientId().contains("@"), "expected an email recipient for " + event.getChannel());
      }
      assertEquals(event.getRecipientId(), event.getUserId());
      assertNotNull(event.getPayload().get("clientApp"));
    }
  }

  @Test
  void sendBatchSimulatedEventsShouldSendExactlyTheRequestedCountWithinBounds() {
    List<NotificationEvent> events = producer.sendBatchSimulatedEvents(5);

    assertEquals(5, events.size());
    verify(kafkaTemplate, times(10)).send(any(), any(), any());
  }

  @Test
  void sendBatchSimulatedEventsShouldCapAt100() {
    List<NotificationEvent> events = producer.sendBatchSimulatedEvents(500);

    assertEquals(100, events.size());
  }

  @Test
  void sendBatchSimulatedEventsShouldFloorAtOneForAZeroOrNegativeCount() {
    assertEquals(1, producer.sendBatchSimulatedEvents(0).size());
    assertEquals(1, producer.sendBatchSimulatedEvents(-5).size());
  }

  @Test
  void getStatusShouldReflectTheInitialIdleState() {
    Map<String, Object> status = producer.getStatus();

    assertEquals(false, status.get("active"));
    assertEquals(2, status.get("ratePerSecond"));
    assertEquals(0L, status.get("totalSent"));
    assertEquals(0L, status.get("totalErrors"));
    assertEquals(KafkaTopicConfig.TOPIC_INGESTION, status.get("targetTopic"));
  }

  @Test
  void startSimulationShouldReturnTrueThenFalseOnAnOverlappingCall() {
    boolean first = producer.startSimulation(5);
    boolean second = producer.startSimulation(5);

    assertTrue(first);
    assertFalse(second);
    assertEquals(true, producer.getStatus().get("active"));
  }

  @Test
  void startSimulationShouldClampTheRateBetween1And20() {
    producer.startSimulation(0);
    assertEquals(1, producer.getStatus().get("ratePerSecond"));
    producer.stopSimulation();

    producer.startSimulation(999);
    assertEquals(20, producer.getStatus().get("ratePerSecond"));
  }

  @Test
  void stopSimulationShouldReturnFalseWhenNothingIsRunning() {
    assertFalse(producer.stopSimulation());
  }

  @Test
  void stopSimulationShouldReturnTrueAndFlipActiveBackToFalseAfterAStart() {
    producer.startSimulation(5);

    boolean stopped = producer.stopSimulation();

    assertTrue(stopped);
    assertEquals(false, producer.getStatus().get("active"));
  }

  @Test
  void cleanupShouldStopAnInFlightSimulationSoItCanBeStartedAgain() {
    producer.startSimulation(5);

    producer.cleanup();

    assertEquals(false, producer.getStatus().get("active"));
    // If cleanup() had not actually stopped it, this would return false
    // (already running) instead of true.
    assertTrue(producer.startSimulation(5));
  }
}
