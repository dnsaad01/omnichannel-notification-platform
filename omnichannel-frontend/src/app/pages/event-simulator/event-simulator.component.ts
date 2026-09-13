import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { BusinessEventService } from '../../services/business-event.service';

interface EventHistoryEntry {
  eventType: string;
  payload: string;
  status: 'SUCCESS' | 'ERROR';
  message: string;
  timestamp: Date;
}

/** A few representative business events used as UI convenience defaults,
 *  not a fixed enum: "Personnalisé" below lets the user publish any
 *  eventType a workflow's TRIGGER node might be configured to match on. */
const PRESETS: Record<string, string> = {
  CART_ABANDONED: JSON.stringify({ recipientId: 'client@example.com', cartValue: 89.90 }, null, 2),
  ORDER_CREATED: JSON.stringify({ recipientId: 'client@example.com', orderId: 'ORD-1001', amount: 129.90 }, null, 2),
  ORDER_SHIPPED: JSON.stringify({ recipientId: 'client@example.com', orderId: 'ORD-1001', carrier: 'DHL' }, null, 2),
  USER_SIGNED_UP: JSON.stringify({ recipientId: 'nouveau@example.com', firstName: 'Sofia' }, null, 2),
  PAYMENT_FAILED: JSON.stringify({ recipientId: 'client@example.com', orderId: 'ORD-1001', reason: 'Carte refusée' }, null, 2)
};

/**
 * Kafka Event Simulator.
 *
 * Distinct from the existing low-level Kafka simulator
 * (services/simulator.service.ts, used by the Dashboard's "Envoi Simulé"
 * button), which fires a raw per-channel NotificationEvent directly at a
 * channel topic and never touches the Workflow Engine at all. This page
 * publishes a *business* event onto notification.events via
 * POST /api/business-events/publish (BusinessEventController) — the only
 * thing WorkflowTriggerConsumer listens on — so it's the actual
 * end-to-end way to fire a workflow from the UI, exactly like a real
 * upstream system (checkout, CRM, etc.) would.
 */
@Component({
  selector: 'app-event-simulator',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './event-simulator.component.html'
})
export class EventSimulatorComponent {
  private businessEventService = inject(BusinessEventService);

  toastIcon: string = 'circle-check-big';

  presetEventTypes = Object.keys(PRESETS);
  selectedPreset = this.presetEventTypes[0];
  useCustomEventType = false;
  customEventType = '';

  payloadText = PRESETS[this.selectedPreset];
  payloadError: string | null = null;

  isPublishing = false;
  toastMessage: string | null = null;
  history: EventHistoryEntry[] = [];

  onPresetChange() {
    if (!this.useCustomEventType) {
      this.payloadText = PRESETS[this.selectedPreset] ?? '{}';
    }
  }

  onToggleCustom() {
    if (this.useCustomEventType) {
      this.payloadText = '{}';
    } else {
      this.payloadText = PRESETS[this.selectedPreset] ?? '{}';
    }
  }

  get effectiveEventType(): string {
    return this.useCustomEventType ? this.customEventType.trim() : this.selectedPreset;
  }

  publish() {
    this.payloadError = null;
    const eventType = this.effectiveEventType;
    if (!eventType) {
      this.payloadError = "Le type d'événement est obligatoire.";
      return;
    }

    let payload: Record<string, any> = {};
    if (this.payloadText?.trim()) {
      try {
        payload = JSON.parse(this.payloadText);
      } catch (e) {
        this.payloadError = 'Le payload doit être un JSON valide.';
        return;
      }
    }

    this.isPublishing = true;
    this.businessEventService.publish({ eventType, payload }).subscribe({
      next: (res) => {
        this.isPublishing = false;
        this.history.unshift({
          eventType,
          payload: this.payloadText,
          status: 'SUCCESS',
          message: `Publié sur notification.events (statut : ${res.status})`,
          timestamp: new Date()
        });
        this.showToast(`Événement "${eventType}" publié avec succès.`, 'circle-check-big');
      },
      error: (err) => {
        this.isPublishing = false;
        const message = err?.error?.message || "Échec de la publication (ingestion-service tourne-t-il sur le port 8082 ?).";
        this.history.unshift({
          eventType,
          payload: this.payloadText,
          status: 'ERROR',
          message,
          timestamp: new Date()
        });
        this.showToast(message, 'triangle-alert');
      }
    });
  }

  clearHistory() {
    this.history = [];
  }

  private showToast(msg: string, icon: string = 'circle-check-big') {
    this.toastMessage = msg;
    this.toastIcon = icon;
    setTimeout(() => {
      if (this.toastMessage === msg) {
        this.toastMessage = null;
      }
    }, 4000);
  }
}
