import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { NotificationRequest } from '../models/notification-request.model';
import { NotificationLogEntry } from '../models/notification-log.model';

/** Backend shape for one row inside GET /api/dashboard/logs/page's
 *  `content` array — same raw shape GET /api/dashboard/logs returns, just
 *  nested under pagination metadata instead of being the whole body. */
interface RawLogEntry {
  id: string;
  user: string;
  channel: string;
  status: string;
  time: string;
}

/**
 * Frontend shape for GET /api/dashboard/logs/page (DashboardController's
 * paginated endpoint, PagedLogsResponse on the backend). `content` is
 * already mapped through the same user -> recipient renaming
 * getRecentLogs() applies, so NotificationsComponent can use it exactly
 * like a NotificationLogEntry[] page.
 */
export interface PagedNotificationLogs {
  content: NotificationLogEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private apiUrl = 'http://localhost:8082/api/v1/notifications';

  /** No GET /api/v1/notifications list endpoint exists on the backend —
   *  see notification-log.model.ts's doc comment for why this reuses the
   *  real /api/dashboard/logs audit endpoint instead. */
  private dashboardApiUrl = 'http://localhost:8082/api/dashboard';

  constructor(private http: HttpClient) { }

  /**
   * POSTs to /api/v1/notifications/send, matching NotificationController's
   * @RequestMapping("/api/v1/notifications") + @PostMapping("/send").
   */
  sendNotification(apiKey: string, request: NotificationRequest): Observable<any> {
    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      'X-API-KEY': apiKey || ''
    });

    return this.http.post<any>(`${this.apiUrl}/send`, request, { headers });
  }

  /**
   * Real notification/delivery-attempt history — GET /api/dashboard/logs,
   * backed by the notification_logs audit table (one row per channel
   * consumer invocation: EmailNotificationConsumer/SmsNotificationConsumer/
   * PushNotificationConsumer). Maps the backend's `user` field onto this
   * app's `recipient` field; everything else passes through as-is.
   *
   * This is a flat, capped list with no pagination metadata — it backs the
   * Dashboard page's small recent-activity widget only. The Notifications
   * page's "Journal des envois" table (300+ rows) uses getRecentLogsPage()
   * below instead, which is what actually paginates.
   */
  getRecentLogs(limit: number = 20): Observable<NotificationLogEntry[]> {
    return this.http
      .get<RawLogEntry[]>(`${this.dashboardApiUrl}/logs`, { params: { limit } })
      .pipe(map(entries => entries.map(e => this.toLogEntry(e))));
  }

  /**
   * GET /api/dashboard/logs/page — the real paginated counterpart to
   * getRecentLogs, matching DashboardController's getRecentLogsPage()
   * endpoint (PagedLogsResponse). Backs the Notifications page's
   * Pagination Controls: with 300+ rows in notification_logs, a single
   * capped fetch (the old `getRecentLogs(50)` call) could only ever show
   * the newest 50 — this lets the table actually walk through every page,
   * and exposes totalElements/totalPages so the UI knows the true count and
   * when "Suivant" should disable itself.
   */
  getRecentLogsPage(page: number = 0, size: number = 50): Observable<PagedNotificationLogs> {
    return this.http
      .get<{ content: RawLogEntry[]; page: number; size: number; totalElements: number; totalPages: number }>(
        `${this.dashboardApiUrl}/logs/page`,
        { params: { page, size } }
      )
      .pipe(
        map(res => ({
          content: res.content.map(e => this.toLogEntry(e)),
          page: res.page,
          size: res.size,
          totalElements: res.totalElements,
          totalPages: res.totalPages
        }))
      );
  }

  /**
   * POST /api/dashboard/logs/{id}/resend — matches DashboardController's
   * new resendLog() endpoint (NotificationResendService). `id` is the same
   * "NOTIF-123" string every row already carries (NotificationLogEntry#id).
   *
   * Fire-and-forget from the caller's perspective: the response only
   * confirms the republish itself succeeded (NotificationResendResponse) —
   * it does not carry a new log row, because that row is written
   * asynchronously, later, by whichever channel consumer actually picks the
   * republished event off Kafka. NotificationsComponent re-fetches the log
   * list right after this resolves, and again after a short delay, to pick
   * up that new row without the user needing to refresh the page — see
   * NotificationsComponent#resendNotification.
   */
  resendNotification(id: string): Observable<any> {
    return this.http.post<any>(`${this.dashboardApiUrl}/logs/${id}/resend`, {});
  }

  private toLogEntry(e: RawLogEntry): NotificationLogEntry {
    return {
      id: e.id,
      recipient: e.user,
      channel: e.channel,
      status: e.status,
      time: e.time
    };
  }

  // Note: getPreferences/updatePreferences against /api/v1/preferences/{userId}
  // were removed here — no such controller exists on the backend, and nothing
  // in the app called these methods. The separate PreferenceService/
  // RecipientPreference feature (same gap, /api/v1/preferences/{recipientId})
  // has since been removed entirely as dead code.
}
