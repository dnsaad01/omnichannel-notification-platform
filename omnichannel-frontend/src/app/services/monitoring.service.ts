import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DlqMessage, DlqReplayResponse, InfrastructureHealthResponse } from '../models/monitoring.model';

@Injectable({
  providedIn: 'root'
})
export class MonitoringService {
  private apiUrl = 'http://localhost:8082/api/monitoring';

  constructor(private http: HttpClient) {}

  /** GET /api/monitoring/health — MonitoringController/MonitoringService,
   *  real live checks (DB via Connection#isValid, Kafka via AdminClient,
   *  Redis via PING), not cached/hardcoded status. */
  getHealth(): Observable<InfrastructureHealthResponse> {
    return this.http.get<InfrastructureHealthResponse>(`${this.apiUrl}/health`);
  }

  /** GET /api/monitoring/dlq — real rows from the dlq_messages table,
   *  populated by DlqMessageConsumer off the notification-dlq Kafka topic.
   *  Replaces the hardcoded dlqMessages array that used to live directly on
   *  MonitoringComponent. */
  getDlqMessages(): Observable<DlqMessage[]> {
    return this.http.get<DlqMessage[]>(`${this.apiUrl}/dlq`);
  }

  /** DELETE /api/monitoring/dlq/{id} — permanently removes the row from
   *  dlq_messages. Nothing re-reads the Kafka topic to rebuild the list, so
   *  a deleted message cannot reappear on refresh or on modal reopen. */
  deleteDlqMessage(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/dlq/${id}`);
  }

  /** POST /api/monitoring/dlq/replay — republishes every stored DLQ message
   *  onto the Kafka topic it originally failed on, then deletes it from
   *  dlq_messages. Backs the "Rejouer Tous les Messages" button. */
  replayDlqMessages(): Observable<DlqReplayResponse> {
    return this.http.post<DlqReplayResponse>(`${this.apiUrl}/dlq/replay`, {});
  }
}
