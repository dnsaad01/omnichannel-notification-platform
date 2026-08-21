package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.NotificationRequest;
import com.eventflow.ingestion.service.NotificationIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = { "http://localhost:4200", "*" }, allowedHeaders = "*")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationIngestionService notificationIngestionService;

    public NotificationController(NotificationIngestionService notificationIngestionService) {
        this.notificationIngestionService = notificationIngestionService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingestNotification(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
            @Valid @RequestBody NotificationRequest request) {

        notificationIngestionService.processNotification(apiKey, request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "ACCEPTED",
                "message", "Notification request successfully queued for processing"));
    }
}
