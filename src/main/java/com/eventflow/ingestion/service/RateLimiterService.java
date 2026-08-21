package com.eventflow.ingestion.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String clientId, int quota) {
        long currentMinute = System.currentTimeMillis() / 60000;
        String key = "rate_limit:" + clientId + ":" + currentMinute;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(60));
        }

        if (count != null && count > quota) {
            log.warn("Rate limit exceeded for clientId: {}. Current count: {}, Quota: {}", clientId, count, quota);
            return false;
        }

        return true;
    }
}
