package com.eventflow.ingestion.workflow.repository;

import com.eventflow.ingestion.workflow.model.WorkflowExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowExecutionLogRepository extends JpaRepository<WorkflowExecutionLog, Long> {
  List<WorkflowExecutionLog> findByExecutionIdOrderByCreatedAtAsc(Long executionId);
}
