package com.eventflow.ingestion.restcontrollers;

import com.eventflow.ingestion.dtos.CostEvaluationResponse;
import com.eventflow.ingestion.entities.RecipientPreference;
import com.eventflow.ingestion.repositories.RecipientPreferenceRepository;
import com.eventflow.ingestion.services.CostOptimizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cost-engine")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CostOptimizationController {

    private final CostOptimizationService costOptimizationService;
    private final RecipientPreferenceRepository recipientPreferenceRepository;

    @GetMapping("/evaluate/{recipientId}")
    public ResponseEntity<CostEvaluationResponse> evaluateOptimalRoute(
            @PathVariable String recipientId,
            @RequestParam(defaultValue = "NORMAL") String priority) {

        RecipientPreference preference = recipientPreferenceRepository
                .findByRecipientId(recipientId)
                .orElse(RecipientPreference.builder()
                        .recipientId(recipientId)
                        .emailEnabled(true)
                        .smsEnabled(true)
                        .pushEnabled(true)
                        .whatsappEnabled(true)
                        .build());

        CostEvaluationResponse response = costOptimizationService.evaluateOptimalChannel(preference, priority);
        return ResponseEntity.ok(response);
    }
}