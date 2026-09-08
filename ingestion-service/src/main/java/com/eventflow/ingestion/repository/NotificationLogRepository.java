package com.eventflow.ingestion.repository;

import com.eventflow.ingestion.model.NotificationLog;
import org.springframework.data.domain.Page;
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

  /**
   * Same rows/ordering as findAllByOrderByCreatedAtDesc above, but returns a
   * Page instead of a bare List, so Spring Data also runs the matching
   * COUNT query and exposes getTotalElements()/getTotalPages(). Kept as a
   * separate method (Java can't overload two methods that differ only by
   * return type) — findAllByOrderByCreatedAtDesc is left exactly as-is
   * since DashboardService#getRecentLogs (backing the Dashboard widget's
   * flat, non-paginated GET /api/dashboard/logs) still uses it and doesn't
   * need a count query on every call. This one backs
   * DashboardService#getRecentLogsPage (GET /api/dashboard/logs/page), the
   * Notifications page's real Pagination Controls.
   */
  @Query("SELECT n FROM NotificationLog n ORDER BY n.createdAt DESC")
  Page<NotificationLog> findAllOrderedByCreatedAtDesc(Pageable pageable);

  @Query("SELECT DISTINCT n.channel FROM NotificationLog n")
  List<String> findDistinctChannels();
}
