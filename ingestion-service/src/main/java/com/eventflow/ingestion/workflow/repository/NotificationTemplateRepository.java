package com.eventflow.ingestion.workflow.repository;

import com.eventflow.ingestion.workflow.model.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
  List<NotificationTemplate> findByChannelIgnoreCase(String channel);

  /** Used by NotificationNodeHandler: a workflow's Notification
   *  node config can reference a template by name instead of numeric id,
   *  which is friendlier to author by hand. */
  Optional<NotificationTemplate> findByNameIgnoreCase(String name);
}
