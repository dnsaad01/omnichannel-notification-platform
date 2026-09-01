package com.eventflow.ingestion.services;

public interface IngestionService {
    boolean processNotification(String recipientId, String channel, Object payload);
}