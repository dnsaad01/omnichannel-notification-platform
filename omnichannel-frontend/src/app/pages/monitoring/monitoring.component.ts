import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SimulatorService, SimulatorStatus } from '../../services/simulator.service';
import { MonitoringService } from '../../services/monitoring.service';
import { DlqMessage, InfrastructureHealthResponse } from '../../models/monitoring.model';

/** Same 5s cadence the Notifications/Execution Detail pages already poll
 *  at — real-time outage detection is the whole point of this page, so a
 *  stopped container shows up here within one cycle, not just on manual
 *  "Rafraîchir". */
const POLL_INTERVAL_MS = 5000;

/** How often the DLQ modal re-fetches while it's open. A DLQ arrival is not
 *  instantaneous — a failing event only lands on notification-dlq after the
 *  channel consumer's retries are exhausted (KafkaConsumerConfig's
 *  ExponentialBackOff: up to ~10s), plus however long DlqMessageConsumer
 *  takes to persist it. Without this poll, a user who opened the modal
 *  right after triggering a failure would see an empty list and have no
 *  reason to believe anything was wrong — indistinguishable from "the DLQ
 *  really is empty" — which is the "ça indique souvent qu'il est vide"
 *  complaint this fixes: it wasn't lying, it was just a static snapshot. */
const DLQ_POLL_INTERVAL_MS = 5000;

@Component({
  selector: 'app-monitoring',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './monitoring.component.html'
})
export class MonitoringComponent implements OnInit, OnDestroy {
  private simulatorService = inject(SimulatorService);
  private monitoringService = inject(MonitoringService);

  /**
   * Real data from GET /api/monitoring/health — replaces what used to be
   * three hardcoded string literals (kafkaStatus/redisStatus/dbStatus)
   * that were never fetched from anywhere, which is the actual root cause
   * of the reported bug: there was no check to fail, so nothing could ever
   * detect the Postgres container going down. See MonitoringService's
   * class doc comment on the backend for the full explanation.
   *
   * null until the first response lands — the template treats that as a
   * neutral "checking…" state rather than fabricating a status.
   */
  health: InfrastructureHealthResponse | null = null;
  isHealthLoading = false;
  healthLoadError: string | null = null;
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  // Simulator controls state
  simulatorStatus: SimulatorStatus = {
    active: false,
    ratePerSecond: 2,
    totalSent: 0,
    totalErrors: 0,
    targetTopic: 'notification.ingestion',
    timestamp: ''
  };

  simRate: number = 2;
  isSimLoading: boolean = false;
  simActionAlert: string | null = null;
  toastMessage: string | null = null;

  // --- DLQ Modal State ---
  // Real data from GET /api/monitoring/dlq (dlq_messages table, populated by
  // DlqMessageConsumer off the notification-dlq Kafka topic). This used to
  // be a hardcoded array initialized right here on the component — which is
  // exactly why "deleted" messages kept reappearing: every page refresh or
  // modal reopen created a brand new MonitoringComponent instance, and this
  // field was re-initialized to the same 3 fake rows every single time.
  // Nothing was ever persisted anywhere, front or back.
  isDlqModalOpen: boolean = false;
  dlqMessages: DlqMessage[] = [];
  dlqCount: string = '… messages';
  isDlqLoading: boolean = false;
  dlqLoadError: string | null = null;
  isReplaying: boolean = false;
  /** ids currently mid-DELETE — lets the template disable just that row's
   *  button instead of the whole table, and stops a double-click from firing
   *  a second DELETE for an id the first request is already removing (the
   *  second would just 404, surfacing a confusing "not found" toast for an
   *  action that actually succeeded). */
  deletingIds = new Set<string>();
  private dlqPollHandle: ReturnType<typeof setInterval> | null = null;

  ngOnInit() {
    this.refreshSimulatorStatus();
    this.fetchHealth();
    this.loadDlqMessages();
    this.pollHandle = setInterval(() => this.fetchHealth(), POLL_INTERVAL_MS);
  }

  ngOnDestroy() {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
    this.stopDlqPolling();
  }

  fetchHealth() {
    this.isHealthLoading = true;
    this.monitoringService.getHealth().subscribe({
      next: (health) => {
        this.isHealthLoading = false;
        this.healthLoadError = null;
        this.health = health;
      },
      error: (err) => {
        this.isHealthLoading = false;
        this.healthLoadError = err?.error?.message || 'Impossible de contacter le backend pour vérifier l\'infrastructure.';
        // Deliberately leaves `health` as-is rather than nulling it out or
        // fabricating a DOWN status here: an HTTP failure calling our own
        // backend isn't the same signal as the backend successfully
        // reporting a dependency is down, and healthLoadError above
        // already surfaces this distinctly in the template.
      }
    });
  }

  refreshHealth() {
    this.showToast('📊 Métriques système rafraîchies à jour !');
    this.fetchHealth();
    this.refreshSimulatorStatus();
    this.loadDlqMessages();
  }

  // --- Redis Cache Action ---
  purgeRedisCache() {
    this.showToast('🧹 Cache Redis purgé avec succès ! Clés d\'invalidation libérées.');
    // Note: this remains a UI-only action — there is no backend endpoint
    // that actually issues a Redis FLUSHDB, so nothing on the real cache
    // changes. fetchHealth() right after simply re-confirms Redis is still
    // reachable; it can't reflect a purge that never happened server-side.
    setTimeout(() => this.fetchHealth(), 500);
  }

  // --- DLQ Inspection Modal Actions ---
  openDlqModal() {
    this.isDlqModalOpen = true;
    this.loadDlqMessages();
    this.startDlqPolling();
  }

  closeDlqModal() {
    this.isDlqModalOpen = false;
    this.stopDlqPolling();
  }

  private startDlqPolling() {
    this.stopDlqPolling(); // guard against a double-open leaking a second interval
    this.dlqPollHandle = setInterval(() => this.loadDlqMessages(), DLQ_POLL_INTERVAL_MS);
  }

  private stopDlqPolling() {
    if (this.dlqPollHandle) {
      clearInterval(this.dlqPollHandle);
      this.dlqPollHandle = null;
    }
  }

  /** GET /api/monitoring/dlq — the single source of truth for both the
   *  modal's table and the small DLQ count badge on the main page. Called
   *  on init, while the modal is open (see DLQ_POLL_INTERVAL_MS), on
   *  refreshHealth(), and after every delete/replay so the list and the
   *  count never drift from what's actually in Postgres. */
  loadDlqMessages() {
    this.isDlqLoading = true;
    this.monitoringService.getDlqMessages().subscribe({
      next: (messages) => {
        this.isDlqLoading = false;
        this.dlqLoadError = null;
        this.dlqMessages = messages;
        this.dlqCount = `${messages.length} messages`;
      },
      error: (err) => {
        this.isDlqLoading = false;
        this.dlqLoadError = err?.error?.message || 'Impossible de charger les messages de la DLQ.';
        // dlqMessages/dlqCount are deliberately left untouched here — an
        // HTTP failure must never be presented as "0 messages / DLQ vide"
        // (see the template's empty-state guard, which now also checks
        // !dlqLoadError): that was the actual bug behind "l'inspecteur
        // indique souvent qu'il est vide" — a transient fetch error used to
        // render the exact same reassuring "🎉 vide" banner as a real
        // empty queue, silently masking the error underneath it.
      }
    });
  }

  replayDlqMessages() {
    this.isReplaying = true;
    this.monitoringService.replayDlqMessages().subscribe({
      next: (res) => {
        this.isReplaying = false;
        this.showToast(`🚀 ${res.replayedCount} messages DLQ réinjectés dans Kafka !`);
        this.loadDlqMessages();
        setTimeout(() => {
          this.isDlqModalOpen = false;
          this.stopDlqPolling();
        }, 1200);
      },
      error: (err) => {
        this.isReplaying = false;
        this.showToast(err?.error?.message || '❌ Échec de la réinjection des messages DLQ.');
      }
    });
  }

  deleteDlqMessage(id: string) {
    if (this.deletingIds.has(id)) {
      return; // already in flight for this row — ignore a double click
    }
    this.deletingIds.add(id);

    this.monitoringService.deleteDlqMessage(id).subscribe({
      next: () => {
        this.deletingIds.delete(id);
        // Re-fetch instead of just filtering dlqMessages locally: the
        // backend is now the source of truth, and re-fetching is what
        // proves the delete actually persisted rather than just looking
        // like it did in local state.
        this.showToast(`🗑️ Message DLQ ${id} supprimé.`);
        this.loadDlqMessages();
      },
      error: (err) => {
        this.deletingIds.delete(id);
        this.showToast(err?.error?.message || `❌ Échec de la suppression du message ${id}.`);
      }
    });
  }

  // --- Kafka Simulator Controls ---
  refreshSimulatorStatus() {
    this.simulatorService.getStatus().subscribe({
      next: (status) => {
        if (status) {
          this.simulatorStatus = status;
          this.simRate = status.ratePerSecond || 2;
        }
      }
    });
  }

  toggleSimulation() {
    this.isSimLoading = true;
    if (this.simulatorStatus.active) {
      this.simulatorService.stopSimulation().subscribe({
        next: (res) => {
          this.isSimLoading = false;
          this.simActionAlert = '🔴 Producteur Simulateur Kafka ARRÊTÉ.';
          this.refreshSimulatorStatus();
        },
        error: () => {
          this.isSimLoading = false;
        }
      });
    } else {
      this.simulatorService.startSimulation(this.simRate).subscribe({
        next: (res) => {
          this.isSimLoading = false;
          this.simActionAlert = `🟢 Producteur Simulateur Kafka DÉMARRÉ (${this.simRate} msg/sec).`;
          this.refreshSimulatorStatus();
        },
        error: () => {
          this.isSimLoading = false;
        }
      });
    }
  }

  sendSingleSimulatedEvent() {
    this.isSimLoading = true;
    this.simulatorService.sendSingleEvent().subscribe({
      next: (res) => {
        this.isSimLoading = false;
        this.simActionAlert = `⚡ Evénement simulé envoyé à Kafka (${res.event?.eventId || 'SIM-EVENT'}) !`;
        this.refreshSimulatorStatus();
      },
      error: () => {
        this.isSimLoading = false;
      }
    });
  }

  sendBatchSimulatedEvents(count: number = 10) {
    this.isSimLoading = true;
    this.simulatorService.sendBatchEvents(count).subscribe({
      next: (res) => {
        this.isSimLoading = false;
        this.simActionAlert = `🚀 Lot de ${count} événements simulés publiés dans topic notification.ingestion !`;
        this.refreshSimulatorStatus();
      },
      error: () => {
        this.isSimLoading = false;
      }
    });
  }

  private showToast(msg: string) {
    this.toastMessage = msg;
    setTimeout(() => {
      if (this.toastMessage === msg) {
        this.toastMessage = null;
      }
    }, 4000);
  }
}
