package com.eventflow.ingestion.dtos;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryFeedbackDTO {
    private String notificationId;
    private String channelType;
    private String recipientId;
    private String status;
    private double latencyMs;
}