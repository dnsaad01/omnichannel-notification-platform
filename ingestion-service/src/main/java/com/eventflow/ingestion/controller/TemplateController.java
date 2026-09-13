package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.workflow.dto.TemplateRequest;
import com.eventflow.ingestion.workflow.dto.TemplateResponse;
import com.eventflow.ingestion.workflow.service.NotificationTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final NotificationTemplateService notificationTemplateService;

    /**
     * Real persistence, replacing the old in-memory stub.
     * Kept as a plain 201 CREATED of the saved template so the existing
     * Angular Templates page (which ignores the response body) keeps working.
     */
    @PostMapping
    public ResponseEntity<TemplateResponse> saveTemplate(@Valid @RequestBody TemplateRequest templateData) {
        TemplateResponse saved = notificationTemplateService.create(templateData);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Endpoint POST pour tester l'envoi d'une notification omnicanale.
     * Reçoit le canal, le destinataire et le message, puis simule l'envoi Kafka.
     * Reste un endpoint de simulation, sans dispatch réel.
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendTestNotification(@RequestBody Map<String, Object> payload) {
        Object channel = payload.getOrDefault("channel", "N/A");
        Object recipient = payload.getOrDefault("recipient", "N/A");
        Object message = payload.getOrDefault("message", "N/A");

        log.info("Dispatch simulé - Channel: {} | Recipient: {} | Message: {}", channel, recipient, message);
        log.info("Publication simulée dans le topic Ingestion: {}", payload);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Notification dispatchée avec succès !");
        response.put("data", payload);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> getAllTemplates() {
        return ResponseEntity.ok(notificationTemplateService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getTemplateById(@PathVariable Long id) {
        return ResponseEntity.ok(notificationTemplateService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TemplateResponse> updateTemplate(@PathVariable Long id, @Valid @RequestBody TemplateRequest templateData) {
        return ResponseEntity.ok(notificationTemplateService.update(id, templateData));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        notificationTemplateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
