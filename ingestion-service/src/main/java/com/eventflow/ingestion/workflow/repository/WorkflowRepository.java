package com.eventflow.ingestion.workflow.repository;

import com.eventflow.ingestion.workflow.model.Workflow;
import com.eventflow.ingestion.workflow.model.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {

  /** Used by WorkflowTriggerConsumer to find which active
   *  workflow(s) should spawn an execution for an incoming business event. */
  List<Workflow> findByStatusAndTriggerEventType(WorkflowStatus status, String triggerEventType);

  List<Workflow> findByStatus(WorkflowStatus status);
}
