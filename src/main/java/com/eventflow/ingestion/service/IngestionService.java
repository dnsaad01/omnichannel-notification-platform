package com.eventflow.ingestion.service;

public interface IngestionService {
    boolean processNotification(String recipientId, String channel, Object payload);
}
