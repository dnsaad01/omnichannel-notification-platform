import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BusinessEventRequest {
  eventType: string;
  payload: Record<string, any>;
}

export interface BusinessEventResponse {
  status: string;
  eventType: string;
  payload: Record<string, any>;
}

/**
 * Kafka Event Simulator page's backend client.
 *
 * Deliberately separate from services/simulator.service.ts: that one hits
 * /api/v1/simulator (low-level, fires raw per-channel NotificationEvents
 * straight at a channel topic, used by the Dashboard's "Envoi Simulé"
 * button). This one hits /api/business-events/publish
 * (BusinessEventController) — a *business* event like CART_ABANDONED that
 * WorkflowTriggerConsumer matches against ACTIVE workflows' trigger config
 * and, on a match, spawns a real WorkflowExecution. Two different layers
 * of the same platform; kept as two different services/pages so neither
 * name shadows what the other actually does.
 */
@Injectable({
  providedIn: 'root'
})
export class BusinessEventService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8082/api/business-events';

  publish(event: BusinessEventRequest): Observable<BusinessEventResponse> {
    return this.http.post<BusinessEventResponse>(`${this.apiUrl}/publish`, event);
  }
}
