package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.UserPreferenceDto;
import com.eventflow.ingestion.model.UserPreference;
import com.eventflow.ingestion.repository.UserPreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@CrossOrigin(origins = { "http://localhost:4200", "*" }, allowedHeaders = "*")
@RestController
@RequestMapping("/api/v1/preferences")
public class UserPreferenceController {

    private final UserPreferenceRepository repository;

    public UserPreferenceController(UserPreferenceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserPreferenceDto> getPreferences(@PathVariable String userId) {
        UserPreference pref = repository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User preference not found for: " + userId));

        return ResponseEntity.ok(toDto(pref));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserPreferenceDto> updatePreferences(
            @PathVariable String userId,
            @RequestBody UserPreferenceDto dto) {

        UserPreference pref = repository.findById(userId)
                .orElse(new UserPreference());

        pref.setUserId(userId);
        if (dto.getEmailAddress() != null) pref.setEmailAddress(dto.getEmailAddress());
        if (dto.getPhoneNumber() != null) pref.setPhoneNumber(dto.getPhoneNumber());
        if (dto.getEmailEnabled() != null) pref.setEnabledEmail(dto.getEmailEnabled());
        if (dto.getSmsEnabled() != null) pref.setEnabledSms(dto.getSmsEnabled());
        if (dto.getPushEnabled() != null) pref.setEnabledPush(dto.getPushEnabled());
        pref.setUpdatedAt(LocalDateTime.now());

        UserPreference saved = repository.save(pref);
        return ResponseEntity.ok(toDto(saved));
    }

    private UserPreferenceDto toDto(UserPreference pref) {
        UserPreferenceDto dto = new UserPreferenceDto();
        dto.setUserId(pref.getUserId());
        dto.setEmailAddress(pref.getEmailAddress());
        dto.setPhoneNumber(pref.getPhoneNumber());
        dto.setEmailEnabled(pref.getEnabledEmail() != null ? pref.getEnabledEmail() : false);
        dto.setSmsEnabled(pref.getEnabledSms() != null ? pref.getEnabledSms() : false);
        dto.setPushEnabled(pref.getEnabledPush() != null ? pref.getEnabledPush() : false);
        return dto;
    }
}
