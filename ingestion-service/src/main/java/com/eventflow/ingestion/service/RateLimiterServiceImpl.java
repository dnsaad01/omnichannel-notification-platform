package com.eventflow.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterServiceImpl implements RateLimiterService {

  private final StringRedisTemplate redisTemplate;

  @Override
  public boolean isAllowed(String clientId, int limitPerMinute) {
    if (clientId == null || clientId.isBlank()) {
      return false;
    }

    String key = "ratelimit:" + clientId;
    try {
      Long currentCount = redisTemplate.opsForValue().increment(key);
      if (currentCount != null && currentCount == 1) {
        redisTemplate.expire(key, Duration.ofMinutes(1));
      }

      boolean allowed = currentCount != null && currentCount <= limitPerMinute;
      if (!allowed) {
        log.warn("Rate limit exceeded for clientId [{}] - count: {}, limit: {}", clientId, currentCount, limitPerMinute);
      }
      return allowed;
    } catch (Exception e) {
      log.warn("Redis rate limiter fallback allowed due to error: {}", e.getMessage());
      return true; // Resilience fallback in case Redis connection fails
    }
  }
}
