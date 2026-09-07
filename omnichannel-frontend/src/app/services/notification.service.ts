import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { NotificationRequest } from '../models/notification-request.model';
import { NotificationLogEntry } from '../models/notification-log.model';

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
   */
  getRecentLogs(limit: number = 20): Observable<NotificationLogEntry[]> {
    return this.http
      .get<Array<{ id: string; user: string; channel: string; status: string; time: string }>>(
        `${this.dashboardApiUrl}/logs`,
        { params: { limit } }
      )
      .pipe(
        map(entries => entries.map(e => ({
          id: e.id,
          recipient: e.user,
          channel: e.channel,
          status: e.status,
          time: e.time
        })))
      );
  }

  // Note: getPreferences/updatePreferences against /api/v1/preferences/{userId}
  // were removed here — no such controller exists on the backend, and nothing
  // in the app called these methods. The separate PreferenceService/
  // RecipientPreference feature (same gap, /api/v1/preferences/{recipientId})
  // has since been removed entirely as dead code.
}
