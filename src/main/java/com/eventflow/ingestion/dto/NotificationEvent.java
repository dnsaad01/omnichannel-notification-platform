package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEvent {

    private String eventId;
    private String userId;
    private String channel;
    private String subject;
    private String body;
    private String timestamp;

    public NotificationEvent() {
    }

    public NotificationEvent(String eventId, String userId, String channel, String subject, String body, String timestamp) {
        this.eventId = eventId;
        this.userId = userId;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.timestamp = timestamp;
    }

    public static NotificationEventBuilder builder() {
        return new NotificationEventBuilder();
    }

    public static class NotificationEventBuilder {
        private String eventId;
        private String userId;
        private String channel;
        private String subject;
        private String body;
        private String timestamp;

        public NotificationEventBuilder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public NotificationEventBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public NotificationEventBuilder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public NotificationEventBuilder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public NotificationEventBuilder body(String body) {
            this.body = body;
            return this;
        }

        public NotificationEventBuilder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public NotificationEvent build() {
            return new NotificationEvent(eventId, userId, channel, subject, body, timestamp);
        }
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
