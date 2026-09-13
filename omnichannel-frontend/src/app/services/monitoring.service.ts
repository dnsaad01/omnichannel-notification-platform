import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { InfrastructureHealthResponse } from '../models/monitoring.model';

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
}
