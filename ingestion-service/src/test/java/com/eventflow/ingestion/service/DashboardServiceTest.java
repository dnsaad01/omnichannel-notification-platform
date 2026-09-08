package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dto.DashboardLogEntry;
import com.eventflow.ingestion.dto.DashboardStatsResponse;
import com.eventflow.ingestion.dto.PagedLogsResponse;
import com.eventflow.ingestion.model.NotificationLog;
import com.eventflow.ingestion.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock
  private NotificationLogRepository notificationLogRepository;

  private DashboardService dashboardService;

  @BeforeEach
  void setUp() {
    dashboardService = new DashboardService(notificationLogRepository);
  }

  @Test
  void shouldComputeSuccessRateAndActiveChannelCountFromRealCounts() {
    when(notificationLogRepository.count()).thenReturn(80L);
    when(notificationLogRepository.countByStatus(NotificationLogService.STATUS_DELIVERED)).thenReturn(60L);
    when(notificationLogRepository.findDistinctChannels()).thenReturn(List.of("EMAIL", "SMS"));

    DashboardStatsResponse stats = dashboardService.getStats();

    assertEquals("80", stats.getTotalSent());
    assertEquals("75.0%", stats.getSuccessRate());
    assertEquals("2", stats.getActiveChannels());
  }

  @Test
  void shouldReturnZeroPercentRatherThanDivideByZeroWhenNothingHasBeenSentYet() {
    when(notificationLogRepository.count()).thenReturn(0L);
    when(notificationLogRepository.countByStatus(NotificationLogService.STATUS_DELIVERED)).thenReturn(0L);
    when(notificationLogRepository.findDistinctChannels()).thenReturn(List.of());

    DashboardStatsResponse stats = dashboardService.getStats();

    assertEquals("0.0%", stats.getSuccessRate());
    assertEquals("0", stats.getActiveChannels());
  }

  @Test
  void shouldMapEachStatusToItsDisplayLabelAndPrefixTheIdWithNOTIF() {
    NotificationLog delivered = NotificationLog.builder()
      .id(42L).recipientId("a@example.com").channel("EMAIL")
      .status(NotificationLogService.STATUS_DELIVERED).createdAt(LocalDateTime.now())
      .build();
    NotificationLog failed = NotificationLog.builder()
      .id(43L).recipientId("+212600000000").channel("SMS")
      .status(NotificationLogService.STATUS_FAILED).createdAt(LocalDateTime.now().minusMinutes(90))
      .build();
    NotificationLog suppressed = NotificationLog.builder()
      .id(44L).recipientId("device-1").channel("PUSH")
      .status(NotificationLogService.STATUS_SUPPRESSED).createdAt(null)
      .build();

    when(notificationLogRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
      .thenReturn(List.of(delivered, failed, suppressed));

    List<DashboardLogEntry> logs = dashboardService.getRecentLogs(20);

    assertEquals(3, logs.size());
    assertEquals("NOTIF-42", logs.get(0).getId());
    assertEquals("Delivered", logs.get(0).getStatus());
    assertEquals("Failed", logs.get(1).getStatus());
    assertEquals("1h ago", logs.get(1).getTime());
    assertEquals("Suppressed", logs.get(2).getStatus());
    assertEquals("unknown", logs.get(2).getTime());
  }

  @Test
  void shouldRequestExactlyOnePageOfTheRequestedSizeFromTheRepository() {
    when(notificationLogRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of());

    dashboardService.getRecentLogs(5);

    verify(notificationLogRepository).findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5));
  }

  // --- getRecentLogsPage: real pagination backing the Notifications page ---

  private NotificationLog aLog(long id) {
    return NotificationLog.builder()
      .id(id).recipientId("user" + id + "@test.com").channel("EMAIL")
      .status(NotificationLogService.STATUS_DELIVERED).createdAt(LocalDateTime.now())
      .build();
  }

  @Test
  void getRecentLogsPageShouldMapContentAndCarryThePageMetadataFromTheRepository() {
    Page<NotificationLog> repoPage = new PageImpl<>(
      List.of(aLog(101L), aLog(102L)), PageRequest.of(2, 50), 312);
    when(notificationLogRepository.findAllOrderedByCreatedAtDesc(PageRequest.of(2, 50))).thenReturn(repoPage);

    PagedLogsResponse response = dashboardService.getRecentLogsPage(2, 50);

    assertEquals(2, response.getContent().size());
    assertEquals("NOTIF-101", response.getContent().get(0).getId());
    assertEquals(2, response.getPage());
    assertEquals(50, response.getSize());
    assertEquals(312, response.getTotalElements());
    // 312 rows at 50/page -> 7 pages (ceil(312/50))
    assertEquals(7, response.getTotalPages());
  }

  @Test
  void getRecentLogsPageShouldClampANegativePageToZero() {
    when(notificationLogRepository.findAllOrderedByCreatedAtDesc(PageRequest.of(0, 50)))
      .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

    PagedLogsResponse response = dashboardService.getRecentLogsPage(-3, 50);

    assertEquals(0, response.getPage());
    verify(notificationLogRepository).findAllOrderedByCreatedAtDesc(PageRequest.of(0, 50));
  }

  @Test
  void getRecentLogsPageShouldClampAnOutOfRangeSizeToAtLeastOneAndAtMostTheMax() {
    when(notificationLogRepository.findAllOrderedByCreatedAtDesc(any(Pageable.class)))
      .thenReturn(new PageImpl<>(List.of()));

    dashboardService.getRecentLogsPage(0, 0);
    verify(notificationLogRepository).findAllOrderedByCreatedAtDesc(PageRequest.of(0, 1));

    dashboardService.getRecentLogsPage(0, 5000);
    verify(notificationLogRepository).findAllOrderedByCreatedAtDesc(PageRequest.of(0, 200));
  }

  @Test
  void getRecentLogsPageShouldReturnZeroTotalPagesWhenThereAreNoLogsAtAll() {
    when(notificationLogRepository.findAllOrderedByCreatedAtDesc(PageRequest.of(0, 50)))
      .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

    PagedLogsResponse response = dashboardService.getRecentLogsPage(0, 50);

    assertEquals(0, response.getTotalElements());
    assertEquals(0, response.getTotalPages());
    assertEquals(0, response.getContent().size());
  }
}
