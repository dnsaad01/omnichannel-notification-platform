package com.eventflow.ingestion.service;

public interface PreferenceCheckerService {
    boolean canSendNotification(String recipientId, String channel);
}
