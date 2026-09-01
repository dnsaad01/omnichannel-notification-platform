package com.eventflow.ingestion.service;

public interface RateLimiterService {
  boolean isAllowed(String clientId, int limitPerMinute);
}
