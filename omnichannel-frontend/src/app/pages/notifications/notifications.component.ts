import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { NotificationFormComponent } from '../../components/notification-form/notification-form.component';
import { NotificationService } from '../../services/notification.service';
import { NotificationLogEntry } from '../../models/notification-log.model';

/** Same 5s cadence WorkflowExecutionDetailComponent already polls at
 *  (see its own POLL_INTERVAL_MS) — kept identical for consistency. */
const POLL_INTERVAL_MS = 5000;

/** How long to wait after a successful POST /send before refreshing, so the
 *  matching channel consumer (EmailNotificationConsumer/etc.) has had a
 *  realistic chance to actually process the Kafka message and write its
 *  notification_logs row — the log endpoint only ever reflects rows that
 *  already exist, it can't show a dispatch that hasn't been consumed yet.
 *  The regular POLL_INTERVAL_MS sweep will pick it up regardless even if
 *  this fires a beat too early. */
const POST_SEND_REFRESH_DELAY_MS = 1500;

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, NotificationFormComponent, LucideAngularModule],
  templateUrl: './notifications.component.html'
})
export class NotificationsComponent implements OnInit, OnDestroy {
  private notificationService = inject(NotificationService);

  showSendForm: boolean = false;

  toggleSendForm(): void {
    this.showSendForm = !this.showSendForm;
  }

  /** Real data only from here on — GET /api/dashboard/logs via
   *  NotificationService#getRecentLogs (see that method's doc comment for
   *  why the dashboard endpoint, not a nonexistent GET /api/v1/notifications).
   *  Starts empty and is populated by the first fetchLogs() in ngOnInit. */
  allNotifications: NotificationLogEntry[] = [];
  isLoading: boolean = false;
  loadError: string | null = null;

  activeFilter: string = 'ALL';
  selectedNotif: NotificationLogEntry | null = null;
  isResending: boolean = false;
  toastMessage: string | null = null;
  toastIcon: string = 'circle-check-big';

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  ngOnInit() {
    this.fetchLogs();
    this.pollHandle = setInterval(() => this.fetchLogs(), POLL_INTERVAL_MS);
  }

  ngOnDestroy() {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  get filteredNotifications(): NotificationLogEntry[] {
    if (this.activeFilter === 'ALL') return this.allNotifications;
    return this.allNotifications.filter(n => n.status.toUpperCase() === this.activeFilter);
  }

  setFilter(filter: string) {
    this.activeFilter = filter;
  }

  selectNotification(notif: NotificationLogEntry) {
    this.selectedNotif = notif;
  }

  /**
   * Bound to NotificationFormComponent's (sent) output (see that component)
   * — fires right after a real POST /api/v1/notifications/send succeeds.
   * Refreshes after a short delay rather than instantly; see
   * POST_SEND_REFRESH_DELAY_MS's doc comment for why. The regular 5s poll
   * below is the reliable fallback either way.
   */
  onNotificationSent() {
    setTimeout(() => this.fetchLogs(), POST_SEND_REFRESH_DELAY_MS);
  }

  /**
   * Still a client-side simulation, unchanged by this pass — flagging
   * rather than silently leaving it misleading now that the table is real.
   * There's no backend "resend" endpoint, and notification_logs never
   * stored the original subject/body (see notification-log.model.ts), so a
   * genuine resend isn't possible from this data alone — it would need a
   * new backend endpoint (resend-by-id, replaying the original request)
   * that hasn't been requested/built. Because the rest of this table is now
   * real, the fake row this injects will visibly vanish on the very next
   * 5s poll (it doesn't exist in notification_logs) — a real regression in
   * how convincing the simulation looks, worth a follow-up if "Relancer"
   * needs to actually work.
   */
  resendNotification(notif: NotificationLogEntry | null) {
    if (!notif) return;

    this.isResending = true;
    setTimeout(() => {
      this.isResending = false;

      const newId = 'NOTIF-' + Math.floor(1026 + Math.random() * 100);
      const nowTime = new Date().toLocaleTimeString('fr-FR');

      const replayed: NotificationLogEntry = {
        id: newId,
        recipient: notif.recipient,
        channel: notif.channel,
        status: 'Delivered',
        time: nowTime,
        payloadSnippet: `[REPLAY de ${notif.id}] ${notif.payloadSnippet || ''}`
      };

      this.allNotifications.unshift(replayed);
      this.selectedNotif = replayed;
      this.showToast(`Notification ${notif.id} relancée avec succès ! (Nouveau log: ${newId})`, 'rocket');
    }, 800);
  }

  refreshLogs() {
    this.fetchLogs(() => this.showToast('Historique des notifications rafraîchi !', 'refresh-cw'));
  }

  private fetchLogs(onDone?: () => void) {
    this.isLoading = true;
    this.notificationService.getRecentLogs(50).subscribe({
      next: (logs) => {
        this.isLoading = false;
        this.loadError = null;
        this.allNotifications = logs;

        // Preserve the current selection across a refresh when it's still
        // present; otherwise fall back to the newest entry so the detail
        // panel never points at a row that's scrolled out of the list.
        const stillPresent = this.selectedNotif
          ? logs.find(n => n.id === this.selectedNotif!.id)
          : undefined;
        this.selectedNotif = stillPresent ?? logs[0] ?? null;

        onDone?.();
      },
      error: (err) => {
        this.isLoading = false;
        this.loadError = err?.error?.message || 'Impossible de charger l\'historique des notifications.';
        onDone?.();
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
