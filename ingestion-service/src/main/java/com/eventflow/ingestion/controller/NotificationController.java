package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.NotificationRequest;
import com.eventflow.ingestion.service.NotificationIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationIngestionService ingestionService;

  @PostMapping("/send")
  public ResponseEntity<?> sendNotification(
    @RequestHeader(value = "X-API-KEY", defaultValue = "default-api-key") String apiKey,
    @Valid @RequestBody NotificationRequest request) {

    ingestionService.processAndPublish(apiKey, request);
    return ResponseEntity.ok(Map.of(
      "status", "ACCEPTED",
      "message", "Notification queued for delivery"
    ));
  }

  @PostMapping("/send-test")
  public ResponseEntity<?> sendTestNotification(@RequestBody Map<String, Object> notificationRequest) {
    System.out.println("📨 Notification de test reçue : " + notificationRequest);

    return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Notification dispatchée avec succès !"));
  }
}
