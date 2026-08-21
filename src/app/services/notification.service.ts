import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { NotificationRequest } from '../models/notification-request.model';
import { UserPreference } from '../models/user-preference.model';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private apiUrl = 'http://localhost:8082/api/v1/notifications';
  private preferencesUrl = 'http://localhost:8082/api/v1/preferences';

  constructor(private http: HttpClient) { }

  sendNotification(apiKey: string, request: NotificationRequest): Observable<any> {
    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      'X-API-KEY': apiKey || ''
    });

    const payload = {
      userId: request.userId,
      channel: request.channel || request.channelType || 'EMAIL',
      subject: request.subject || request.title || 'Notification',
      body: request.body
    };

    return this.http.post<any>(this.apiUrl, payload, { headers });
  }

  getPreferences(userId: string): Observable<UserPreference> {
    return this.http.get<UserPreference>(`${this.preferencesUrl}/${userId}`);
  }

  updatePreferences(userId: string, prefs: UserPreference): Observable<UserPreference> {
    return this.http.put<UserPreference>(`${this.preferencesUrl}/${userId}`, prefs);
  }
}
