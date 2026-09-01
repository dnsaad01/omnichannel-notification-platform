package com.eventflow.ingestion.services;

public interface PreferenceCheckerService {
    boolean canSendNotification(String recipientId, String channel);
}