import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, WorkflowExecutionResponse } from '../models/workflow.model';

/**
 * Read-only access to WorkflowExecutions — backed by
 * WorkflowExecutionController (ingestion-service, Phase 1),
 * /api/workflow-executions. There is deliberately no create/update/delete
 * here: executions are only ever spawned by the backend's
 * WorkflowTriggerConsumer, never launched by hand (the project's core rule).
 */
@Injectable({
  providedIn: 'root'
})
export class WorkflowExecutionService {
  private apiUrl = 'http://localhost:8082/api/workflow-executions';

  constructor(private http: HttpClient) {}

  list(options: { workflowId?: number; status?: string; page?: number; size?: number } = {}): Observable<Page<WorkflowExecutionResponse>> {
    let params = new HttpParams()
      .set('page', String(options.page ?? 0))
      .set('size', String(options.size ?? 20));

    if (options.workflowId != null) {
      params = params.set('workflowId', String(options.workflowId));
    }
    if (options.status) {
      params = params.set('status', options.status);
    }

    return this.http.get<Page<WorkflowExecutionResponse>>(this.apiUrl, { params });
  }

  getById(id: number): Observable<WorkflowExecutionResponse> {
    return this.http.get<WorkflowExecutionResponse>(`${this.apiUrl}/${id}`);
  }
}
