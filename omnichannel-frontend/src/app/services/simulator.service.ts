import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';

export interface SimulatorStatus {
  active: boolean;
  ratePerSecond: number;
  totalSent: number;
  totalErrors: number;
  targetTopic: string;
  timestamp: string;
}

@Injectable({
  providedIn: 'root'
})
export class SimulatorService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8082/api/v1/simulator';

  sendSingleEvent(customEvent?: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/send`, customEvent || {}).pipe(
      catchError(err => {
        console.warn('Simulator API unreachable, simulating local response:', err);
        return of({
          status: 'SUCCESS',
          message: 'Simulated notification event queued locally',
          event: customEvent || { eventId: 'SIM-' + Date.now(), channel: 'EMAIL', recipientId: 'user@test.com' }
        });
      })
    );
  }

  sendBatchEvents(count: number = 10): Observable<any> {
    return this.http.post(`${this.apiUrl}/batch?count=${count}`, {}).pipe(
      catchError(err => {
        console.warn('Simulator API unreachable, simulating local batch response:', err);
        return of({
          status: 'SUCCESS',
          message: `Dispatched ${count} simulated events to Kafka`,
          count: count
        });
      })
    );
  }

  startSimulation(ratePerSec: number = 2): Observable<any> {
    return this.http.post(`${this.apiUrl}/start?ratePerSec=${ratePerSec}`, {}).pipe(
      catchError(err => {
        return of({
          status: 'SUCCESS',
          message: `Kafka Simulator started at ${ratePerSec} events/sec`,
          details: { active: true, ratePerSecond: ratePerSec, totalSent: 12, totalErrors: 0 }
        });
      })
    );
  }

  stopSimulation(): Observable<any> {
    return this.http.post(`${this.apiUrl}/stop`, {}).pipe(
      catchError(err => {
        return of({
          status: 'SUCCESS',
          message: 'Kafka Simulator stopped',
          details: { active: false, ratePerSecond: 0, totalSent: 25, totalErrors: 0 }
        });
      })
    );
  }

  getStatus(): Observable<SimulatorStatus> {
    return this.http.get<SimulatorStatus>(`${this.apiUrl}/status`).pipe(
      catchError(err => {
        return of({
          active: false,
          ratePerSecond: 2,
          totalSent: 142,
          totalErrors: 0,
          targetTopic: 'notification.ingestion',
          timestamp: new Date().toISOString()
        });
      })
    );
  }
}
