import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface NotificationLog {
  id: string;
  recipient: string;
  channel: 'EMAIL' | 'SMS' | 'PUSH';
  priority: 'HIGH' | 'MEDIUM' | 'LOW' | 'URGENT';
  status: 'DELIVERED' | 'PENDING' | 'FAILED';
  timestamp: string;
}

export interface DltMessage {
  id: string;
  eventId: string;
  topic: string;
  userId: string;
  recipient: string;
  channel: string;
  failureReason: string;
  retryCount: number;
  payloadJson: string;
  timestamp: string;
}

@Component({
  selector: 'app-analytics-overview',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './analytics-overview.component.html',
  styleUrl: './analytics-overview.component.scss'
})
export class AnalyticsOverviewComponent {
  // Top Metrics
  totalVolume: number = 128450;
  kafkaThroughput: number = 1420;
  deliverySuccessRate: number = 99.82;
  dltCount: number = 2;

  // Feedback Messages
  actionFeedback: string | null = null;
  feedbackType: 'success' | 'info' = 'success';

  // Selected DLT Message for Inspector
  selectedDltIndex: number = 0;

  // Notification Logs Table
  recentLogs: NotificationLog[] = [
    { id: 'evt_90812', recipient: 'usr_1001 (user1@example.com)', channel: 'EMAIL', priority: 'HIGH', status: 'DELIVERED', timestamp: 'Just now' },
    { id: 'evt_90813', recipient: 'usr_1002 (+155501928)', channel: 'SMS', priority: 'URGENT', status: 'DELIVERED', timestamp: '1m ago' },
    { id: 'evt_90814', recipient: 'usr_1003 (token_apns_88)', channel: 'PUSH', priority: 'MEDIUM', status: 'PENDING', timestamp: '2m ago' },
    { id: 'evt_90815', recipient: 'usr_1004 (invalid_email@bad.com)', channel: 'EMAIL', priority: 'HIGH', status: 'FAILED', timestamp: '4m ago' },
    { id: 'evt_90816', recipient: 'usr_1005 (+199900088)', channel: 'SMS', priority: 'LOW', status: 'DELIVERED', timestamp: '5m ago' }
  ];

  // DLT Messages
  dltMessages: DltMessage[] = [
    {
      id: 'dlt_001',
      eventId: 'evt_dlt_4091',
      topic: 'notification.events.DLT',
      userId: 'usr_789',
      recipient: 'invalid_user@domain.com',
      channel: 'EMAIL',
      failureReason: 'SMTP 550 5.1.1 User unknown / Host unreachable',
      retryCount: 3,
      payloadJson: `{\n  "eventId": "evt_dlt_4091",\n  "userId": "usr_789",\n  "channel": "EMAIL",\n  "recipient": "invalid_user@domain.com",\n  "retryCount": 3,\n  "lastError": "ConnectionTimeout: Mail server 10.0.4.12:25 refused connection"\n}`,
      timestamp: '12m ago'
    },
    {
      id: 'dlt_002',
      eventId: 'evt_dlt_4092',
      topic: 'notification.events.DLT',
      userId: 'usr_902',
      recipient: '+1000000000',
      channel: 'SMS',
      failureReason: 'SMS Gateway 400 Invalid E.164 phone number format',
      retryCount: 3,
      payloadJson: `{\n  "eventId": "evt_dlt_4092",\n  "userId": "usr_902",\n  "channel": "SMS",\n  "recipient": "+1000000000",\n  "retryCount": 3,\n  "lastError": "InvalidRecipient: Destination address format unparseable"\n}`,
      timestamp: '28m ago'
    }
  ];

  get currentDltMessage(): DltMessage | null {
    return this.dltMessages[this.selectedDltIndex] || null;
  }

  selectDltItem(index: number): void {
    this.selectedDltIndex = index;
  }

  requeueDltMessage(): void {
    const msg = this.currentDltMessage;
    if (!msg) return;

    this.dltMessages.splice(this.selectedDltIndex, 1);
    this.dltCount = this.dltMessages.length;
    this.selectedDltIndex = Math.max(0, this.selectedDltIndex - 1);

    this.showFeedback(`✅ Event ${msg.eventId} re-queued to Kafka topic "notification.events.retry"`, 'success');
  }

  purgeDltMessage(): void {
    const msg = this.currentDltMessage;
    if (!msg) return;

    this.dltMessages.splice(this.selectedDltIndex, 1);
    this.dltCount = this.dltMessages.length;
    this.selectedDltIndex = Math.max(0, this.selectedDltIndex - 1);

    this.showFeedback(`🗑️ Event ${msg.eventId} permanently purged from Dead Letter Topic`, 'success');
  }

  refreshMetrics(): void {
    // Simulate a refresh with slightly randomized values
    this.totalVolume += Math.floor(Math.random() * 200 + 50);
    this.kafkaThroughput = Math.floor(Math.random() * 200 + 1300);
    this.showFeedback('📊 Analytics metrics refreshed from Kafka stream.', 'info');
  }

  private showFeedback(msg: string, type: 'success' | 'info' = 'success'): void {
    this.actionFeedback = msg;
    this.feedbackType = type;
    setTimeout(() => {
      this.actionFeedback = null;
    }, 4500);
  }
}
