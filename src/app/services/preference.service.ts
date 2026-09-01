import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RecipientPreference } from '../models/recipient-preference';

@Injectable({
  providedIn: 'root'
})
export class PreferenceService {
  private readonly apiUrl = 'http://localhost:8080/api/v1/preferences';

  constructor(private http: HttpClient) {}

  getPreferences(recipientId: string): Observable<RecipientPreference> {
    return this.http.get<RecipientPreference>(`${this.apiUrl}/${recipientId}`);
  }

  savePreferences(preference: RecipientPreference): Observable<RecipientPreference> {
    return this.http.post<RecipientPreference>(this.apiUrl, preference);
  }
}
