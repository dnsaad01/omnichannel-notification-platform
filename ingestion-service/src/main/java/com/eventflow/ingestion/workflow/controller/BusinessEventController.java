package com.eventflow.ingestion.workflow.controller;

import com.eventflow.ingestion.config.KafkaTopicConfig;
import com.eventflow.ingestion.workflow.dto.BusinessEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * The backend half of the Kafka Event Simulator — publishes a business
 * event onto notification.events, where WorkflowTriggerConsumer picks it
 * up. This is the entry point for exercising the whole engine end-to-end
 * with nothing but curl:
 *
 * curl -X POST http://localhost:8082/api/business-events/publish \
 *   -H "Content-Type: application/json" \
 *   -d '{"eventType":"CART_ABANDONED","payload":{"recipientId":"client@example.com","cartValue":89.90}}'
 */
@Slf4j
@RestController
@RequestMapping("/api/business-events")
@RequiredArgsConstructor
public class BusinessEventController {

  private final KafkaTemplate<String, BusinessEvent> kafkaTemplate;

  @PostMapping("/publish")
  public ResponseEntity<Map<String, Object>> publish(@Valid @RequestBody BusinessEvent event) {
    log.info("Publishing business event [{}] onto {}", event.getEventType(), KafkaTopicConfig.TOPIC_BUSINESS_EVENTS);
    kafkaTemplate.send(KafkaTopicConfig.TOPIC_BUSINESS_EVENTS, event.getEventType(), event);

    Map<String, Object> response = new HashMap<>();
    response.put("status", "PUBLISHED");
    response.put("eventType", event.getEventType());
    response.put("payload", event.getPayload());
    return ResponseEntity.ok(response);
  }
}
