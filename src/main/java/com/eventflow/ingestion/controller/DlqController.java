package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.DlqMessageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@CrossOrigin(origins = { "http://localhost:4200", "*" }, allowedHeaders = "*")
@RestController
@RequestMapping("/api/v1/dlq")
public class DlqController {

    private final Map<String, DlqMessageDto> dlqStore = new ConcurrentHashMap<>();

    public DlqController() {
        initSampleData();
    }

    private void initSampleData() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        DlqMessageDto m1 = new DlqMessageDto(
                "evt_dlq_1001",
                "SMS_GATEWAY_TWILIO",
                "Invalid Phone Number (E.164 format parsing error)",
                now,
                Map.of("kafkaTopic", "notification.events.DLT", "partition", 2, "offset", 14092, "retryCount", 3, "producerId", "ingestion-service-pod-2"),
                Map.of("userId", "usr_8820", "channel", "SMS", "recipient", "+100000000", "message", "Your verification code is 891230", "priority", "HIGH")
        );

        DlqMessageDto m2 = new DlqMessageDto(
                "evt_dlq_1002",
                "EMAIL_SMTP_MAILPIT",
                "Mailbox Full (552 5.2.2 Storage quota exceeded)",
                now,
                Map.of("kafkaTopic", "notification.events.DLT", "partition", 0, "offset", 8812, "retryCount", 3, "producerId", "ingestion-service-pod-1"),
                Map.of("userId", "usr_4401", "channel", "EMAIL", "recipient", "full_mailbox@enterprise.org", "subject", "Monthly Billing Statement Ready", "body", "Please review your monthly invoice attached.", "priority", "MEDIUM")
        );

        dlqStore.put(m1.getEventId(), m1);
        dlqStore.put(m2.getEventId(), m2);
    }

    @GetMapping
    public ResponseEntity<List<DlqMessageDto>> getDlqMessages() {
        return ResponseEntity.ok(new ArrayList<>(dlqStore.values()));
    }

    @PostMapping("/{eventId}/retry")
    public ResponseEntity<Map<String, Object>> retryDlqMessage(
            @PathVariable String eventId,
            @RequestBody(required = false) Map<String, Object> updatedPayload) {

        if (!dlqStore.containsKey(eventId)) {
            return ResponseEntity.notFound().build();
        }

        DlqMessageDto message = dlqStore.remove(eventId);
        if (updatedPayload != null && !updatedPayload.isEmpty()) {
            message.setPayload(updatedPayload);
        }

        return ResponseEntity.ok(Map.of(
                "status", "REQUEUED",
                "message", "Event " + eventId + " successfully re-driven to active ingestion queue.",
                "eventId", eventId
        ));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Map<String, Object>> purgeDlqMessage(@PathVariable String eventId) {
        if (!dlqStore.containsKey(eventId)) {
            return ResponseEntity.notFound().build();
        }

        dlqStore.remove(eventId);

        return ResponseEntity.ok(Map.of(
                "status", "PURGED",
                "message", "Event " + eventId + " permanently purged from Dead Letter Queue.",
                "eventId", eventId
        ));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> purgeBulkDlqMessages(@RequestBody(required = false) List<String> eventIds) {
        int count;
        if (eventIds != null && !eventIds.isEmpty()) {
            eventIds.forEach(dlqStore::remove);
            count = eventIds.size();
        } else {
            count = dlqStore.size();
            dlqStore.clear();
        }

        return ResponseEntity.ok(Map.of(
                "status", "PURGED",
                "message", count + " event(s) purged from Dead Letter Queue.",
                "count", count
        ));
    }
}
