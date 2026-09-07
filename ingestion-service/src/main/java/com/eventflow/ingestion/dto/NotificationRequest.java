package com.eventflow.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    @NotBlank(message = "Recipient ID is required")
    private String recipientId;

    @NotBlank(message = "Channel is required")
    private String channel;

    private String priority;
    private String templateId;
    private String subject;
    private String body;
    private Map<String, Object> payload;
}
