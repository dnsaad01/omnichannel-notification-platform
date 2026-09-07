package com.eventflow.ingestion.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_preferences")
public class UserPreference {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String userId;

  private boolean enabledEmail;
  private boolean enabledSms;
  private boolean enabledPush;

  private LocalTime quietHoursStart;
  private LocalTime quietHoursEnd;

  public boolean isEnabledEmail() {
    return enabledEmail;
  }

  public boolean isEnabledSms() {
    return enabledSms;
  }

  public boolean isEnabledPush() {
    return enabledPush;
  }
}
