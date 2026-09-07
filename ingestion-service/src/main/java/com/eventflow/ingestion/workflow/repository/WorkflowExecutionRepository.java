package com.eventflow.ingestion.workflow.repository;

import com.eventflow.ingestion.workflow.model.ExecutionStatus;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

  Page<WorkflowExecution> findByWorkflowId(Long workflowId, Pageable pageable);

  Page<WorkflowExecution> findByStatus(ExecutionStatus status, Pageable pageable);

  /**
   * Phase 1's wait-resume scheduler claim query (see architecture plan,
   * section 2.3 / 5): atomically flips every due WAITING row to ADVANCING
   * and returns which ids it actually claimed, so the caller knows exactly
   * which executions to re-publish onto the advance topic — safe even if
   * multiple service instances run this concurrently, since the UPDATE is
   * serialized by Postgres.
   */
  @Modifying
  @Query(value = """
    UPDATE workflow_executions
    SET status = 'ADVANCING', version = version + 1
    WHERE status = 'WAITING' AND next_wake_at <= :now
    RETURNING id
    """, nativeQuery = true)
  List<Long> claimDueWaitingExecutions(@Param("now") LocalDateTime now);

  /**
   * Backs StatisticsService's email open-rate metric — the only place in
   * the codebase that reads context_json as jsonb rather than deserializing
   * it to a Map in Java. context_json holds nested keys written by
   * ContextUtils#set(context, "email.sent"/"email.opened", ...) — see that
   * method's own doc comment — i.e. {"email": {"sent": true, "opened": true,
   * ...}, ...}, so `context_json -> 'email' ->> 'sent'` reads the nested
   * text value directly via Postgres's own jsonb operators, matching how
   * `next_wake_at` above already goes straight at the native column rather
   * than through JPQL.
   *
   * "sent" here only ever becomes true via
   * NotificationNodeHandler#dispatchEmailSynchronously (the Workflow
   * Engine's EMAIL path) — see StatisticsService#computeOpenRate for why
   * this count is scoped to workflow-triggered emails only, not every EMAIL
   * row in notification_logs.
   */
  @Query(value = "SELECT COUNT(*) FROM workflow_executions WHERE context_json -> 'email' ->> 'sent' = 'true'", nativeQuery = true)
  long countEmailSent();

  /** Companion to countEmailSent above — "opened" is set true only by
   *  EmailTrackingService#recordOpen, i.e. only once the tracking pixel
   *  (TrackingController, GET /api/tracking/open/{executionId}) was
   *  actually fetched by a real mail client. */
  @Query(value = "SELECT COUNT(*) FROM workflow_executions WHERE context_json -> 'email' ->> 'opened' = 'true'", nativeQuery = true)
  long countEmailOpened();
}
