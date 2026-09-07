package com.eventflow.ingestion.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * A business event — the thing a Trigger node matches on. Doubles as both
 * the request body for POST /api/business-events/publish and the payload
 * published onto KafkaTopicConfig.TOPIC_BUSINESS_EVENTS (the two shapes are
 * identical, so one class serves both without duplication).
 *
 * Example: { "eventType": "CART_ABANDONED", "payload": { "userId": "u123",
 * "cartValue": 89.90, "email": "client@example.com" } }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessEvent {

  @NotBlank(message = "eventType is required")
  private String eventType;

  private Map<String, Object> payload;
}
