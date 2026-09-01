package com.eventflow.ingestion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    public boolean isAllowed(String clientId, int limitPerMinute) {
        String key = "ratelimit:" + clientId;
        Long currentCount = redisTemplate.opsForValue().increment(key);
        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        return currentCount != null && currentCount <= limitPerMinute;
    }
}
