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

    @PostMapping("/ingest")
    public ResponseEntity<?> ingestNotification(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @Valid @RequestBody NotificationRequest request) {
        
        ingestionService.processAndPublish(apiKey, request);
        return ResponseEntity.ok(Map.of(
                "status", "ACCEPTED",
                "message", "Notification queued for delivery"
        ));
    }
}
