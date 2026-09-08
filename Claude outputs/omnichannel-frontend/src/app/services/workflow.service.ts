import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WorkflowRequest, WorkflowResponse } from '../models/workflow.model';

/**
 * Real Workflow CRUD/lifecycle, backed by WorkflowController
 * (ingestion-service, Phase 1) — /api/workflows. Phase 2's Workflows page
 * only uses the read methods (getAllWorkflows/getWorkflowById); create/
 * update/activate are already real on the backend and wired here ahead of
 * the Builder UI (Phase 3), which is what will actually call them.
 */
@Injectable({
  providedIn: 'root'
})
export class WorkflowService {
  private apiUrl = 'http://localhost:8082/api/workflows';

  constructor(private http: HttpClient) {}

  getAllWorkflows(): Observable<WorkflowResponse[]> {
    return this.http.get<WorkflowResponse[]>(this.apiUrl);
  }

  getWorkflowById(id: number): Observable<WorkflowResponse> {
    return this.http.get<WorkflowResponse>(`${this.apiUrl}/${id}`);
  }

  /** Payload is WorkflowRequest — {name, description, triggerEventType,
   *  definitionJson}, where definitionJson already carries the full
   *  {nodes, edges} graph (see WorkflowBuilderComponent#onSave). */
  createWorkflow(workflowData: WorkflowRequest): Observable<WorkflowResponse> {
    return this.http.post<WorkflowResponse>(this.apiUrl, workflowData);
  }

  updateWorkflow(id: number, workflowData: WorkflowRequest): Observable<WorkflowResponse> {
    return this.http.put<WorkflowResponse>(`${this.apiUrl}/${id}`, workflowData);
  }

  deleteWorkflow(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  activateWorkflow(id: number): Observable<WorkflowResponse> {
    return this.http.post<WorkflowResponse>(`${this.apiUrl}/${id}/activate`, {});
  }

  deactivateWorkflow(id: number): Observable<WorkflowResponse> {
    return this.http.post<WorkflowResponse>(`${this.apiUrl}/${id}/deactivate`, {});
  }

  duplicateWorkflow(id: number): Observable<WorkflowResponse> {
    return this.http.post<WorkflowResponse>(`${this.apiUrl}/${id}/duplicate`, {});
  }
}
