package com.eventflow.ingestion.controller;

import com.eventflow.ingestion.dto.DlqMessageResponse;
import com.eventflow.ingestion.dto.DlqReplayResponse;
import com.eventflow.ingestion.dto.InfrastructureHealthResponse;
import com.eventflow.ingestion.service.DlqManagementService;
import com.eventflow.ingestion.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Backs the Angular Monitoring page (pages/monitoring) with real, live-checked
 * infrastructure status — see MonitoringService for what each check actually
 * does and why the old page could never detect an outage.
 *
 * Always returns 200 for /health, even when healthy=false — the response
 * body IS the status; a down dependency is data for the frontend to render,
 * not an HTTP-level failure of this endpoint itself.
 *
 * The /dlq endpoints back the DLQ Inspection Modal. They used to have
 * nothing behind them at all — MonitoringComponent kept a hardcoded
 * dlqMessages array and "deleted" entries by filtering that local array in
 * memory, so every page refresh (a fresh component instance) reset it back
 * to the same 3 fake messages. DlqMessageConsumer now persists every real
 * record off the notification-dlq Kafka topic into the dlq_messages table,
 * and these endpoints read/mutate that table — see DlqManagementService.
 */
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

  private final MonitoringService monitoringService;
  private final DlqManagementService dlqManagementService;

  @GetMapping("/health")
  public ResponseEntity<InfrastructureHealthResponse> getHealth() {
    return ResponseEntity.ok(monitoringService.getHealth());
  }

  @GetMapping("/dlq")
  public ResponseEntity<List<DlqMessageResponse>> getDlqMessages() {
    return ResponseEntity.ok(dlqManagementService.listMessages());
  }

  @DeleteMapping("/dlq/{id}")
  public ResponseEntity<Void> deleteDlqMessage(@PathVariable Long id) {
    dlqManagementService.deleteMessage(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/dlq/replay")
  public ResponseEntity<DlqReplayResponse> replayDlqMessages() {
    return ResponseEntity.ok(dlqManagementService.replayAll());
  }
}
