package com.eventflow.ingestion.dto;

import java.util.Map;

public class DlqMessageDto {
    private String eventId;
    private String channelId;
    private String failureReason;
    private String timestamp;
    private Map<String, Object> headers;
    private Object payload;

    public DlqMessageDto() {}

    public DlqMessageDto(String eventId, String channelId, String failureReason, String timestamp, Map<String, Object> headers, Object payload) {
        this.eventId = eventId;
        this.channelId = channelId;
        this.failureReason = failureReason;
        this.timestamp = timestamp;
        this.headers = headers;
        this.payload = payload;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public Map<String, Object> getHeaders() { return headers; }
    public void setHeaders(Map<String, Object> headers) { this.headers = headers; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
