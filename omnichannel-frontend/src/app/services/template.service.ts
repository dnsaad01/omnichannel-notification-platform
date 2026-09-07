import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface TemplateResponse {
  id: number;
  name: string;
  channel: string;
  subject?: string;
  body: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TemplateRequest {
  name: string;
  channel: string;
  subject?: string;
  body: string;
  status?: string;
}

/**
 * Phase 4: extended with getTemplateById/updateTemplate/deleteTemplate to
 * back the Templates page's upgrade from create-only to full CRUD. All
 * three endpoints already existed on the backend since Phase 0
 * (TemplateController) — only this service and the page itself were
 * lagging behind it.
 */
@Injectable({
  providedIn: 'root'
})
export class TemplateService {
  private apiUrl = 'http://localhost:8082/api/templates';

  constructor(private http: HttpClient) {}

  getAllTemplates(): Observable<TemplateResponse[]> {
    return this.http.get<TemplateResponse[]>(this.apiUrl);
  }

  getTemplateById(id: number): Observable<TemplateResponse> {
    return this.http.get<TemplateResponse>(`${this.apiUrl}/${id}`);
  }

  saveTemplate(templateData: TemplateRequest): Observable<TemplateResponse> {
    return this.http.post<TemplateResponse>(this.apiUrl, templateData);
  }

  updateTemplate(id: number, templateData: TemplateRequest): Observable<TemplateResponse> {
    return this.http.put<TemplateResponse>(`${this.apiUrl}/${id}`, templateData);
  }

  deleteTemplate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  testSendTemplate(payload: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/send`, payload);
  }
}
