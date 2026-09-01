package com.eventflow.ingestion.services;

import com.eventflow.ingestion.dtos.CostEvaluationResponse;
import com.eventflow.ingestion.entities.RecipientPreference;
import com.eventflow.ingestion.repositories.ChannelMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CostOptimizationServiceImpl implements CostOptimizationService {

    private final ChannelMetricRepository metricRepository;

    @Override
    public CostEvaluationResponse evaluateOptimalChannel(RecipientPreference preference, String priority) {
        if ("HIGH".equalsIgnoreCase(priority)) {
            return CostEvaluationResponse.builder()
                    .recommendedChannel("SMS")
                    .estimatedCost(0.05)
                    .channelSuccessRate(99.0)
                    .rationale("High priority dispatch routes via immediate SMS direct carrier.")
                    .build();
        }

        List<String> allowedChannels = new ArrayList<>();
        if (preference != null) {
            if (preference.isPushEnabled()) allowedChannels.add("PUSH");
            if (preference.isEmailEnabled()) allowedChannels.add("EMAIL");
            if (preference.isWhatsappEnabled()) allowedChannels.add("WHATSAPP");
            if (preference.isSmsEnabled()) allowedChannels.add("SMS");
        }

        if (allowedChannels.isEmpty()) {
            return CostEvaluationResponse.builder()
                    .recommendedChannel("NONE")
                    .estimatedCost(0.0)
                    .channelSuccessRate(0.0)
                    .rationale("Recipient has opted out of all communication channels.")
                    .build();
        }

        String optimal = allowedChannels.get(0);
        double cost = getChannelBaseCost(optimal);

        return CostEvaluationResponse.builder()
                .recommendedChannel(optimal)
                .estimatedCost(cost)
                .channelSuccessRate(98.5)
                .rationale("Selected lowest-cost opted-in delivery vector.")
                .build();
    }

    private double getChannelBaseCost(String channel) {
        return switch (channel.toUpperCase()) {
            case "PUSH" -> 0.000;
            case "EMAIL" -> 0.001;
            case "WHATSAPP" -> 0.030;
            case "SMS" -> 0.050;
            default -> 0.010;
        };
    }
}
