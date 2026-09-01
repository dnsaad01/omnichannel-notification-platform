import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CostEvaluationResponse } from '../models/cost-evaluation.model';

@Injectable({
  providedIn: 'root'
})
export class RuleSimulatorService {
  private apiUrl = 'http://localhost:8080/api/v1/cost-engine';

  constructor(private http: HttpClient) {}

  simulateRoute(recipientId: string, priority: string): Observable<CostEvaluationResponse> {
    const params = new HttpParams().set('priority', priority);
    return this.http.get<CostEvaluationResponse>(`${this.apiUrl}/evaluate/${recipientId}`, { params });
  }
}
