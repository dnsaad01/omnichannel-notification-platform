package com.eventflow.ingestion.workflow.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One run of a Workflow, created only by WorkflowTriggerConsumer — never
 * manually, per the project's core rule that users configure workflows but
 * never launch them by hand.
 *
 * `version` (@Version, JPA optimistic locking) guards against two advance
 * attempts racing on the same execution — e.g. the wait-resume scheduler and
 * a redelivered Kafka message both trying to move this row forward at once.
 * A concurrent write bumps this field; the loser gets an
 * OptimisticLockException and simply retries against the fresh row.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
  name = "workflow_executions",
  indexes = {
    @Index(name = "idx_execution_status_wake", columnList = "status, nextWakeAt"),
    @Index(name = "idx_execution_workflow", columnList = "workflowId")
  }
)
public class WorkflowExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long workflowId;

  /** Pins this execution to the exact graph it started with — an edit to the
   *  live workflow (which creates a new version) never changes the behavior
   *  of executions already in flight. */
  private Integer workflowVersion;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private ExecutionStatus status = ExecutionStatus.RUNNING;

  private String currentNodeId;

  /** Starts as the raw incoming business event; mutated in place as nodes
   *  execute (e.g. context.email.sent = true). Native jsonb, same mapping
   *  approach as Workflow.definitionJson. */
  @JdbcTypeCode(SqlTypes.JSON)
  private String contextJson;

  /** Set when status = WAITING; the polling scheduler claims rows where this
   *  is in the past (see ExecutionStatus.ADVANCING). Null otherwise. */
  private LocalDateTime nextWakeAt;

  @Version
  private Integer version;

  private LocalDateTime startedAt;
  private LocalDateTime lastActivityAt;
  private LocalDateTime completedAt;

  @PrePersist
  public void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.startedAt = now;
    this.lastActivityAt = now;
  }

  @PreUpdate
  public void onUpdate() {
    this.lastActivityAt = LocalDateTime.now();
  }
}
