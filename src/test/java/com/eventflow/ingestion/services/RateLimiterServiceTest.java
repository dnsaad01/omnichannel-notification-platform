package com.eventflow.ingestion.services;

import com.eventflow.ingestion.service.RateLimiterService;
import com.eventflow.ingestion.service.RateLimiterServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private RateLimiterService rateLimiterService;

  @BeforeEach
  void setUp() {
    rateLimiterService = new RateLimiterServiceImpl(redisTemplate);
  }

  @Test
  void shouldAllowRequestWithinLimit() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("ratelimit:client-123")).thenReturn(1L);

    boolean allowed = rateLimiterService.isAllowed("client-123", 10);

    assertTrue(allowed);
    verify(redisTemplate).expire(eq("ratelimit:client-123"), any(Duration.class));
  }

  @Test
  void shouldRejectRequestWhenLimitExceeded() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("ratelimit:client-123")).thenReturn(11L);

    boolean allowed = rateLimiterService.isAllowed("client-123", 10);

    assertFalse(allowed);
  }
}
