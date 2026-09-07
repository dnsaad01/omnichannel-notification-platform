package com.eventflow.ingestion.repository;

import com.eventflow.ingestion.model.NotificationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

  long countByStatus(String status);

  /** Per-channel breakdown for StatisticsService — same table/columns as
   *  countByStatus above, just grouped implicitly via one call per channel
   *  rather than a GROUP BY, so it returns plain longs like every other
   *  count method here instead of needing a projection type. */
  long countByChannel(String channel);

  long countByChannelAndStatus(String channel, String status);

  List<NotificationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Query("SELECT DISTINCT n.channel FROM NotificationLog n")
  List<String> findDistinctChannels();
}
