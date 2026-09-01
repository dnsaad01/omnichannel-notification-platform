package com.eventflow.ingestion.dtos;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostEvaluationResponse {
    private String recommendedChannel;
    private double estimatedCost;
    private double channelSuccessRate;
    private String rationale;
}