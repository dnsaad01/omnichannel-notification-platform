package com.eventflow.ingestion.workflow.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Real persistence for reusable notification templates, replacing the old
 * TemplateController stub that only echoed what it received. A workflow's
 * Notification node references one of these by id — it never embeds message
 * content directly (see the architecture plan, section 3).
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notification_templates")
public class NotificationTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  /** EMAIL, SMS, or PUSH — kept as a plain String for consistency with how
   *  channel is represented everywhere else in this codebase (NotificationRequest,
   *  NotificationEvent, the 3 channel consumers all use String, not an enum). */
  private String channel;

  /** Mainly relevant for EMAIL; null/ignored for SMS and PUSH. */
  private String subject;

  /** Message body with {{variable}} placeholders resolved against a workflow
   *  execution's context at send time. */
  @Builder.Default
  private String body = "";

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private TemplateStatus status = TemplateStatus.ACTIVE;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @PrePersist
  public void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  public void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
