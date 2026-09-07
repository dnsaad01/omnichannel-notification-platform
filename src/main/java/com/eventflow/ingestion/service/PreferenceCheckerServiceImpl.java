package com.eventflow.ingestion.service;

import com.eventflow.ingestion.entities.RecipientPreference;
import com.eventflow.ingestion.repository.RecipientPreferenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreferenceCheckerServiceImpl implements PreferenceCheckerService {

    private final RecipientPreferenceRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String REDIS_PREFIX = "pref:";

    @Override
    public boolean canSendNotification(String recipientId, String channel) {
        String cacheKey = REDIS_PREFIX + recipientId;
        RecipientPreference preference = null;

        // 1. Redis Cache First
        Object cachedObj = redisTemplate.opsForValue().get(cacheKey);
        if (cachedObj != null) {
            preference = objectMapper.convertValue(cachedObj, RecipientPreference.class);
            log.info("Preference retrieved from Redis cache for: {}", recipientId);
        }

        // 2. Database Fallback (PostgreSQL)
        if (preference == null) {
            log.info("Cache miss for: {}. Fetching from PostgreSQL...", recipientId);
            preference = repository.findByRecipientId(recipientId)
                    .orElseGet(() -> RecipientPreference.builder()
                            .recipientId(recipientId)
                            .emailEnabled(true)
                            .smsEnabled(true)
                            .pushEnabled(true)
                            .whatsappEnabled(true)
                            .build());

            // Cache for 1 hour
            redisTemplate.opsForValue().set(cacheKey, preference, 1, TimeUnit.HOURS);
        }

        // 3. Quiet Hours Check
        if (isInQuietHours(preference)) {
            log.warn("Notification blocked: Recipient {} is in quiet hours.", recipientId);
            return false;
        }

        // 4. Channel Opt-in Check
        return isChannelAllowed(preference, channel);
    }

    private boolean isChannelAllowed(RecipientPreference pref, String channel) {
        return switch (channel.toUpperCase()) {
            case "EMAIL" -> pref.isEmailEnabled();
            case "SMS" -> pref.isSmsEnabled();
            case "PUSH" -> pref.isPushEnabled();
            case "WHATSAPP" -> pref.isWhatsappEnabled();
            default -> false;
        };
    }

    private boolean isInQuietHours(RecipientPreference pref) {
        if (pref.getQuietHoursStart() == null || pref.getQuietHoursEnd() == null) {
            return false;
        }
        LocalTime now = LocalTime.now();
        if (pref.getQuietHoursStart().isBefore(pref.getQuietHoursEnd())) {
            return !now.isBefore(pref.getQuietHoursStart()) && now.isBefore(pref.getQuietHoursEnd());
        } else {
            return !now.isBefore(pref.getQuietHoursStart()) || now.isBefore(pref.getQuietHoursEnd());
        }
    }
}
