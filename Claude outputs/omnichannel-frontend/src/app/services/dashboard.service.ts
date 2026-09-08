import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private apiUrl = 'http://localhost:8082/api/dashboard'; // رابط الـ Backend للـ Dashboard

  constructor(private http: HttpClient) {}

  getStats(): Observable<any> {
    return this.http.get(`${this.apiUrl}/stats`);
  }

  getRecentLogs(): Observable<any> {
    return this.http.get(`${this.apiUrl}/logs`);
  }
}
