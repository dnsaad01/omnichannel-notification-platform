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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * A Workflow definition — the graph an admin builds in the drag-and-drop
 * builder. definitionJson is the full { nodes: [...], edges: [...] } graph,
 * stored as native Postgres jsonb via Hibernate 6's built-in JSON mapping
 * (no extra dependency needed beyond spring-boot-starter-data-jpa).
 *
 * Versioning: editing an ACTIVE workflow produces a new row with the same
 * name but version + 1 and status DRAFT (see WorkflowStatus). Only one
 * version of a given workflow "identity" should be ACTIVE at a time — that
 * invariant is enforced in the Phase 1 activation endpoint, not here.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
  name = "workflows",
  indexes = {
    @Index(name = "idx_workflow_trigger_event_type", columnList = "triggerEventType"),
    @Index(name = "idx_workflow_status", columnList = "status")
  }
)
public class Workflow {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  private String description;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private WorkflowStatus status = WorkflowStatus.DRAFT;

  /** Denormalized from the graph's TRIGGER node — this is what
   *  WorkflowTriggerConsumer (Phase 1) filters ACTIVE workflows on. */
  private String triggerEventType;

  @Builder.Default
  private Integer version = 1;

  /** Set only on a copy-on-write DRAFT revision (Phase 1, WorkflowService#update):
   *  editing an ACTIVE workflow never mutates its row in place — it inserts a
   *  new row with version + 1 and parentWorkflowId = the row being revised, so
   *  in-flight WorkflowExecutions pinned to the old row's id keep running
   *  against its frozen definitionJson. Activating the draft archives the
   *  parent. Null for a workflow's first version. */
  private Long parentWorkflowId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Builder.Default
  private String definitionJson = "{\"nodes\":[],\"edges\":[]}";

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
