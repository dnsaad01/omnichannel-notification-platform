import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationFormComponent } from '../../components/notification-form/notification-form.component';
import { NotificationService } from '../../services/notification.service';
import { NotificationLogEntry } from '../../models/notification-log.model';

/** Same 5s cadence WorkflowExecutionDetailComponent already polls at
 *  (see its own POLL_INTERVAL_MS) — kept identical for consistency. */
const POLL_INTERVAL_MS = 5000;

/** How long to wait after a successful POST /send (or /resend) before
 *  refreshing again, so the matching channel consumer
 *  (EmailNotificationConsumer/etc.) has had a realistic chance to actually
 *  process the Kafka message and write its notification_logs row — the log
 *  endpoint only ever reflects rows that already exist, it can't show a
 *  dispatch that hasn't been consumed yet. The regular POLL_INTERVAL_MS
 *  sweep will pick it up regardless even if this fires a beat too early. */
const POST_SEND_REFRESH_DELAY_MS = 1500;

/** Default page size for the "Journal des envois" table — matches the old
 *  hardcoded getRecentLogs(50) call this replaces, so the table's default
 *  density doesn't change, only its ability to reach the rows past 50. */
const DEFAULT_PAGE_SIZE = 50;

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, NotificationFormComponent],
  templateUrl: './notifications.component.html'
})
export class NotificationsComponent implements OnInit, OnDestroy {
  private notificationService = inject(NotificationService);

  showSendForm: boolean = false;

  toggleSendForm(): void {
    this.showSendForm = !this.showSendForm;
  }

  /** Real data only from here on — GET /api/dashboard/logs/page via
   *  NotificationService#getRecentLogsPage (see that method's doc comment
   *  for why the paginated dashboard endpoint, not a nonexistent
   *  GET /api/v1/notifications). allNotifications only ever holds the
   *  CURRENT page's rows, not the whole table — see the pagination state
   *  below. Starts empty and is populated by the first fetchLogs() in
   *  ngOnInit. */
  allNotifications: NotificationLogEntry[] = [];
  isLoading: boolean = false;
  loadError: string | null = null;

  // --- Pagination state ---
  // ⚠️ Root cause of the reported bug: the table used to call
  // getRecentLogs(50) — a flat, single-shot fetch capped at 50 rows, with
  // no way to ask the backend for anything past that. With 300+ rows in
  // notification_logs, "Tous (50)" was silently hiding every row beyond the
  // 50 most recent ones; there was no page/size concept anywhere in this
  // component, and the backend endpoint it called didn't return a total
  // count either. Fixed by switching to GET /api/dashboard/logs/page
  // (DashboardService#getRecentLogsPage), which returns totalElements/
  // totalPages alongside each page's rows.
  currentPage: number = 0;
  pageSize: number = DEFAULT_PAGE_SIZE;
  totalPages: number = 0;
  totalElements: number = 0;

  activeFilter: string = 'ALL';
  selectedNotif: NotificationLogEntry | null = null;
  isResending: boolean = false;
  toastMessage: string | null = null;

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

  /** Filtering is applied client-side, on the current page's rows only —
   *  it does not query the backend or search across other pages. That
   *  matches how this filter always worked (it filtered whatever was in
   *  allNotifications before pagination existed too); a filter that
   *  searches the full 300+ row table across pages would need real
   *  server-side filtering, which hasn't been asked for here. */
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

  /** Moves to the previous page and re-fetches it. No-op (rather than
   *  going negative) when already on the first page — mirrors the
   *  template's [disabled] guard so a stray call (e.g. a fast double-click
   *  before Angular re-renders the disabled state) can't request page -1. */
  goToPreviousPage() {
    if (this.currentPage <= 0 || this.isLoading) return;
    this.currentPage--;
    this.fetchLogs();
  }

  /** Moves to the next page and re-fetches it. No-op once currentPage is
   *  the last page (totalPages - 1) or there are no pages at all
   *  (totalPages === 0, an empty table) — same reasoning as
   *  goToPreviousPage above. */
  goToNextPage() {
    if (this.isLoading || this.currentPage + 1 >= this.totalPages) return;
    this.currentPage++;
    this.fetchLogs();
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
   * Real backend call as of this fix — POST /api/dashboard/logs/{id}/resend
   * (NotificationResendService), not a client-side simulation.
   *
   * ⚠️ Root cause of the reported bug ("le toast vert disparaît au refresh,
   * la liste ne se met jamais à jour"): this used to setTimeout() and then
   * splice a fabricated NotificationLogEntry straight into
   * allNotifications, with no backend call at all. Nothing was ever
   * persisted, so the injected row — and the toast referencing it — vanished
   * on the very next 5s poll or manual refresh, which is exactly the
   * symptom reported.
   *
   * Now: the backend republishes a real event onto Kafka, which the normal
   * channel-consumer pipeline picks up and turns into a genuine new
   * notification_logs row. That row doesn't exist the instant this HTTP
   * call resolves (the consumer hasn't processed it yet), so this refetches
   * twice — immediately, so the toast/UI settle right away without waiting
   * for the 5s poll, and again after POST_SEND_REFRESH_DELAY_MS (same
   * pattern as onNotificationSent) to actually pick up the new row once
   * it's been written. Either way, the user never has to refresh manually.
   */
  resendNotification(notif: NotificationLogEntry | null) {
    if (!notif || this.isResending) return;

    this.isResending = true;
    this.notificationService.resendNotification(notif.id).subscribe({
      next: () => {
        this.isResending = false;
        this.showToast(`🚀 Notification ${notif.id} relancée avec succès !`);
        this.fetchLogs();
        this.onNotificationSent();
      },
      error: (err) => {
        this.isResending = false;
        this.showToast(err?.error?.message || `❌ Échec de la relance de la notification ${notif.id}.`);
      }
    });
  }

  refreshLogs() {
    this.fetchLogs(() => this.showToast('🔄 Historique des notifications rafraîchi !'));
  }

  private fetchLogs(onDone?: () => void) {
    this.isLoading = true;
    this.notificationService.getRecentLogsPage(this.currentPage, this.pageSize).subscribe({
      next: (result) => {
        this.isLoading = false;
        this.loadError = null;
        this.allNotifications = result.content;
        this.totalElements = result.totalElements;
        this.totalPages = result.totalPages;

        // Preserve the current selection across a refresh when it's still
        // present on this page; otherwise fall back to this page's newest
        // entry so the detail panel never points at a row that scrolled
        // off (either polled away, or onto a different page).
        const stillPresent = this.selectedNotif
          ? result.content.find(n => n.id === this.selectedNotif!.id)
          : undefined;
        this.selectedNotif = stillPresent ?? result.content[0] ?? null;

        onDone?.();
      },
      error: (err) => {
        this.isLoading = false;
        this.loadError = err?.error?.message || 'Impossible de charger l\'historique des notifications.';
        onDone?.();
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
