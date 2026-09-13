import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { SimulatorService, SimulatorStatus } from '../../services/simulator.service';
import { MonitoringService } from '../../services/monitoring.service';
import { InfrastructureHealthResponse } from '../../models/monitoring.model';

interface DlqMessage {
  id: string;
  recipient: string;
  channel: string;
  errorReason: string;
  timestamp: string;
}

/** Same 5s cadence the Notifications/Execution Detail pages already poll
 *  at — real-time outage detection is the whole point of this page, so a
 *  stopped container shows up here within one cycle, not just on manual
 *  "Rafraîchir". */
const POLL_INTERVAL_MS = 5000;

@Component({
  selector: 'app-monitoring',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
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

  /** Still mocked, unchanged by this pass — out of scope for the
   *  infrastructure-outage-detection fix this session, but flagging rather
   *  than leaving it silently misleading now that the three cards above it
   *  are real: there's no backend endpoint listing real DLQ messages (the
   *  `notification-dlq` Kafka topic is real — see KafkaConsumerConfig/
   *  KafkaTopicConfig — but nothing consumes/lists it over REST), so
   *  dlqMessages below and this count are both still fabricated. */
  dlqCount = '3 messages';

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
  simActionIcon: string = 'zap';
  toastMessage: string | null = null;
  toastIcon: string = 'circle-check-big';

  // DLQ Modal State
  isDlqModalOpen: boolean = false;
  dlqMessages: DlqMessage[] = [
    { id: 'DLQ-901', recipient: 'invalid_email_format.com', channel: 'EMAIL', errorReason: 'SMTP 550 Invalid Recipient', timestamp: '10:14:22' },
    { id: 'DLQ-902', recipient: '+212000000000', channel: 'SMS', errorReason: 'Twilio Unreachable Carrier', timestamp: '11:05:01' },
    { id: 'DLQ-903', recipient: 'push_token_expired_x88', channel: 'PUSH', errorReason: 'FCM Token Registration Expired', timestamp: '12:30:15' }
  ];

  ngOnInit() {
    this.refreshSimulatorStatus();
    this.fetchHealth();
    this.pollHandle = setInterval(() => this.fetchHealth(), POLL_INTERVAL_MS);
  }

  ngOnDestroy() {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
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
    this.showToast('Métriques système rafraîchies à jour !', 'chart-column');
    this.fetchHealth();
    this.refreshSimulatorStatus();
  }

  // --- Redis Cache Action ---
  purgeRedisCache() {
    this.showToast('Cache Redis purgé avec succès ! Clés d\'invalidation libérées.', 'eraser');
    // Note: this remains a UI-only action — there is no backend endpoint
    // that actually issues a Redis FLUSHDB, so nothing on the real cache
    // changes. fetchHealth() right after simply re-confirms Redis is still
    // reachable; it can't reflect a purge that never happened server-side.
    setTimeout(() => this.fetchHealth(), 500);
  }

  // --- DLQ Inspection Modal Actions ---
  openDlqModal() {
    this.isDlqModalOpen = true;
  }

  closeDlqModal() {
    this.isDlqModalOpen = false;
  }

  replayDlqMessages() {
    const replayedCount = this.dlqMessages.length;
    this.dlqMessages = [];
    this.dlqCount = '0 messages';
    this.showToast(`${replayedCount} messages DLQ réinjectés dans Kafka (notification-retry) !`, 'rocket');
    setTimeout(() => {
      this.isDlqModalOpen = false;
    }, 1200);
  }

  deleteDlqMessage(id: string) {
    this.dlqMessages = this.dlqMessages.filter(m => m.id !== id);
    this.dlqCount = `${this.dlqMessages.length} messages`;
    this.showToast(`Message DLQ ${id} supprimé.`, 'trash-2');
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
          this.simActionAlert = 'Producteur Simulateur Kafka ARRÊTÉ.';
          this.simActionIcon = 'square';
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
          this.simActionAlert = `Producteur Simulateur Kafka DÉMARRÉ (${this.simRate} msg/sec).`;
          this.simActionIcon = 'play';
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
        this.simActionAlert = `Evénement simulé envoyé à Kafka (${res.event?.eventId || 'SIM-EVENT'}) !`;
        this.simActionIcon = 'zap';
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
        this.simActionAlert = `Lot de ${count} événements simulés publiés dans topic notification.ingestion !`;
        this.simActionIcon = 'rocket';
        this.refreshSimulatorStatus();
      },
      error: () => {
        this.isSimLoading = false;
      }
    });
  }

  private showToast(msg: string, icon: string = 'circle-check-big') {
    this.toastMessage = msg;
    this.toastIcon = icon;
    setTimeout(() => {
      if (this.toastMessage === msg) {
        this.toastMessage = null;
      }
    }, 4000);
  }
}
