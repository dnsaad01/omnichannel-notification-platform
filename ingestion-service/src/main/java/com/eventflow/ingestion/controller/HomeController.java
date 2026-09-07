package com.eventflow.ingestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        return ResponseEntity.ok(Map.of(
                "service", "Omnichannel Notification Ingestion Service",
                "status", "UP",
                "endpoints", Map.of(
                        "POST /api/v1/notifications", "Submit a new notification (Requires header: X-API-KEY)"
                )
        ));
    }
}
