package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String eventId;
    private String recipientId;
    private String userId;
    private String channel;
    private String priority;
    private String templateId;
    private String subject;
    private String body;
    private Map<String, Object> payload;
    private LocalDateTime createdAt;

    public String getUserId() {
        return userId != null ? userId : recipientId;
    }

    public String getRecipientId() {
        return recipientId != null ? recipientId : userId;
    }
}
