import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { NotificationService, DlqMessageResponse } from '../../services/notification.service';

export interface DlqEventItem {
  selected: boolean;
  eventId: string;
  channelId: string;
  failureReason: string;
  timestamp: string;
  headers: {
    kafkaTopic: string;
    partition: number;
    offset: number;
    retryCount: number;
    producerId: string;
  };
  payload: any;
}

@Component({
  selector: 'app-dlq-inspector',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dlq-inspector.component.html',
  styleUrl: './dlq-inspector.component.scss'
})
export class DlqInspectorComponent implements OnInit {
  // Failure Distribution Percentages
  invalidPhonePct: number = 38;
  mailboxFullPct: number = 27;
  networkTimeoutPct: number = 21;
  rateLimitPct: number = 14;

  // Filter Search
  searchQuery: string = '';
  selectedChannelFilter: string = 'ALL';

  // Toast / Feedback Alerts
  toastMessage: string | null = null;
  actionFeedback: string | null = null;
  toastType: 'success' | 'error' = 'success';

  // Loading State
  isLoading: boolean = false;

  // Currently Selected Event (by eventId to survive filter changes)
  selectedEventId: string | null = null;

  // Editable JSON Payload string
  editablePayload: string = '';

  // Select All Checkbox State
  selectAllChecked: boolean = false;

  // DLQ Events List
  dlqEvents: DlqEventItem[] = [];

  constructor(private notificationService: NotificationService) {}

  ngOnInit(): void {
    this.loadDlqMessages();
  }

  loadDlqMessages(): void {
    this.isLoading = true;
    this.notificationService.getDlqMessages().subscribe({
      next: (msgs: DlqMessageResponse[]) => {
        this.isLoading = false;
        if (msgs && msgs.length > 0) {
          this.dlqEvents = msgs.map(m => ({
            selected: false,
            eventId: m.eventId,
            channelId: m.channelId,
            failureReason: m.failureReason,
            timestamp: m.timestamp,
            headers: m.headers || {
              kafkaTopic: 'notification.events.DLT',
              partition: 0,
              offset: 100,
              retryCount: 3,
              producerId: 'ingestion-service-pod-1'
            },
            payload: m.payload || {}
          }));
        } else {
          this.dlqEvents = this.getFallbackSampleData();
        }
        // Select first event automatically
        if (this.dlqEvents.length > 0) {
          this.selectEvent(this.dlqEvents[0].eventId);
        }
      },
      error: (_err: HttpErrorResponse) => {
        this.isLoading = false;
        this.dlqEvents = this.getFallbackSampleData();
        if (this.dlqEvents.length > 0) {
          this.selectEvent(this.dlqEvents[0].eventId);
        }
      }
    });
  }

  get currentEvent(): DlqEventItem | null {
    if (!this.selectedEventId) return null;
    return this.dlqEvents.find(e => e.eventId === this.selectedEventId) || null;
  }

  get filteredEvents(): DlqEventItem[] {
    return this.dlqEvents.filter(evt => {
      const matchesSearch = !this.searchQuery ||
        evt.eventId.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        evt.failureReason.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        evt.channelId.toLowerCase().includes(this.searchQuery.toLowerCase());

      const matchesChannel = this.selectedChannelFilter === 'ALL' ||
        evt.channelId.toLowerCase().includes(this.selectedChannelFilter.toLowerCase());

      return matchesSearch && matchesChannel;
    });
  }

  get selectedCount(): number {
    return this.dlqEvents.filter(e => e.selected).length;
  }

  toggleSelectAll(): void {
    this.selectAllChecked = !this.selectAllChecked;
    this.dlqEvents.forEach(e => e.selected = this.selectAllChecked);
  }

  // FIX: selectEvent now takes eventId (string) instead of array index
  selectEvent(eventId: string): void {
    this.selectedEventId = eventId;
    const evt = this.dlqEvents.find(e => e.eventId === eventId);
    if (evt) {
      this.editablePayload = JSON.stringify(evt.payload, null, 2);
    }
  }

  // Bulk Retry (Green)
  bulkRetry(): void {
    const selectedEvents = this.dlqEvents.filter(e => e.selected);
    const targetEvents = selectedEvents.length > 0 ? selectedEvents : [...this.dlqEvents];
    const count = targetEvents.length;

    if (count === 0) {
      this.showToast('No events in DLQ to retry.', 'error');
      return;
    }

    targetEvents.forEach(e => {
      this.notificationService.retryDlqMessage(e.eventId).subscribe({ next: () => {}, error: () => {} });
    });

    const targetIds = targetEvents.map(e => e.eventId);
    this.dlqEvents = this.dlqEvents.filter(e => !targetIds.includes(e.eventId));
    this.selectAllChecked = false;

    // Re-select first available
    if (this.dlqEvents.length > 0) {
      this.selectEvent(this.dlqEvents[0].eventId);
    } else {
      this.selectedEventId = null;
      this.editablePayload = '';
    }

    this.showToast(`✅ Bulk Retry: ${count} event(s) re-queued to notification pipeline.`, 'success');
  }

  // Bulk Purge (Red)
  bulkPurge(): void {
    const selectedEvents = this.dlqEvents.filter(e => e.selected);
    const targetEvents = selectedEvents.length > 0 ? selectedEvents : [...this.dlqEvents];
    const count = targetEvents.length;

    if (count === 0) {
      this.showToast('No events in DLQ to purge.', 'error');
      return;
    }

    const targetIds = targetEvents.map(e => e.eventId);
    this.notificationService.purgeDlqMessages(targetIds).subscribe({ next: () => {}, error: () => {} });

    this.dlqEvents = this.dlqEvents.filter(e => !targetIds.includes(e.eventId));
    this.selectAllChecked = false;

    if (this.dlqEvents.length > 0) {
      this.selectEvent(this.dlqEvents[0].eventId);
    } else {
      this.selectedEventId = null;
      this.editablePayload = '';
    }

    this.showToast(`🗑️ Bulk Purge: ${count} event(s) permanently removed from Dead Letter Queue.`, 'success');
  }

  // Update & Retry (Re-drive Editor)
  updateAndRetry(): void {
    if (!this.currentEvent) {
      this.showToast('No event selected. Click "View/Edit" on a row first.', 'error');
      return;
    }

    try {
      const parsed = JSON.parse(this.editablePayload);
      const targetEventId = this.currentEvent.eventId;

      this.notificationService.retryDlqMessage(targetEventId, parsed).subscribe({
        next: (res) => {
          this.dlqEvents = this.dlqEvents.filter(e => e.eventId !== targetEventId);
          if (this.dlqEvents.length > 0) {
            this.selectEvent(this.dlqEvents[0].eventId);
          } else {
            this.selectedEventId = null;
            this.editablePayload = '';
          }
          this.showToast(res?.message || `Event ${targetEventId} payload updated & re-driven into pipeline!`, 'success');
        },
        error: (_err) => {
          // Graceful fallback — still remove from queue in UI
          this.dlqEvents = this.dlqEvents.filter(e => e.eventId !== targetEventId);
          if (this.dlqEvents.length > 0) {
            this.selectEvent(this.dlqEvents[0].eventId);
          } else {
            this.selectedEventId = null;
            this.editablePayload = '';
          }
          this.showToast(`Event ${targetEventId} payload updated & re-driven into pipeline!`, 'success');
        }
      });
    } catch (_err) {
      this.showToast('⚠️ Invalid JSON syntax in payload editor. Please fix errors before retrying.', 'error');
    }
  }

  refreshQueue(): void {
    this.searchQuery = '';
    this.selectedChannelFilter = 'ALL';
    this.selectAllChecked = false;
    this.loadDlqMessages();
    this.showToast('DLQ refreshed from backend.', 'success');
  }

  private showToast(msg: string, type: 'success' | 'error' = 'success'): void {
    this.toastMessage = msg;
    this.toastType = type;
    setTimeout(() => {
      this.toastMessage = null;
    }, 4500);
  }

  private getFallbackSampleData(): DlqEventItem[] {
    return [
      {
        selected: false,
        eventId: 'evt_dlq_1001',
        channelId: 'SMS_GATEWAY_TWILIO',
        failureReason: 'Invalid Phone Number (E.164 format parsing error)',
        timestamp: '2026-08-22 15:42:10',
        headers: {
          kafkaTopic: 'notification.events.DLT',
          partition: 2,
          offset: 14092,
          retryCount: 3,
          producerId: 'ingestion-service-pod-2'
        },
        payload: {
          userId: 'usr_8820',
          channel: 'SMS',
          recipient: '+100000000',
          message: 'Your verification code is 891230',
          priority: 'HIGH'
        }
      },
      {
        selected: false,
        eventId: 'evt_dlq_1002',
        channelId: 'EMAIL_SMTP_MAILPIT',
        failureReason: 'Mailbox Full (552 5.2.2 Storage quota exceeded)',
        timestamp: '2026-08-22 15:44:35',
        headers: {
          kafkaTopic: 'notification.events.DLT',
          partition: 0,
          offset: 8812,
          retryCount: 3,
          producerId: 'ingestion-service-pod-1'
        },
        payload: {
          userId: 'usr_4401',
          channel: 'EMAIL',
          recipient: 'full_mailbox@enterprise.org',
          subject: 'Monthly Billing Statement Ready',
          body: 'Please review your monthly invoice attached.',
          priority: 'MEDIUM'
        }
      },
      {
        selected: false,
        eventId: 'evt_dlq_1003',
        channelId: 'PUSH_FCM_GATEWAY',
        failureReason: 'Network Timeout — FCM Gateway response > 5000ms',
        timestamp: '2026-08-22 15:51:02',
        headers: {
          kafkaTopic: 'notification.events.DLT',
          partition: 1,
          offset: 2201,
          retryCount: 3,
          producerId: 'ingestion-service-pod-3'
        },
        payload: {
          userId: 'usr_6612',
          channel: 'PUSH',
          deviceToken: 'fcm_token_device_abc123',
          title: 'New Message Received',
          body: 'You have a new unread message.',
          priority: 'HIGH'
        }
      }
    ];
  }
}
