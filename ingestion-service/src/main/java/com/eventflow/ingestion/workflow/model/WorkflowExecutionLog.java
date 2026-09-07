package com.eventflow.ingestion.workflow.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One line in an execution's Timeline (see the Execution Detail page in the
 * plan, section 7 / spec section 15). Append-only — never updated.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
  name = "workflow_execution_logs",
  indexes = {
    @Index(name = "idx_execution_log_execution_id", columnList = "executionId")
  }
)
public class WorkflowExecutionLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long executionId;

  /** Null for the two synthetic entries logged before any node runs
   *  ("Event reçu depuis Kafka"). */
  private String nodeId;
  private String nodeType;

  private String message;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private LogLevel level = LogLevel.INFO;

  private LocalDateTime createdAt;

  @PrePersist
  public void onCreate() {
    this.createdAt = LocalDateTime.now();
  }
}
