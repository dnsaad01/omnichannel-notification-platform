package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dtos.CostEvaluationResponse;
import com.eventflow.ingestion.entities.RecipientPreference;

public interface CostOptimizationService {
    CostEvaluationResponse evaluateOptimalChannel(RecipientPreference preference, String priority);
}
