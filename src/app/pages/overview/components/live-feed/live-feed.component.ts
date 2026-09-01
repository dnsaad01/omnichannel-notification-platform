import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Mail, MessageSquare, Bell, CheckCircle2, AlertTriangle } from 'lucide-angular';

@Component({
  selector: 'app-live-feed',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="bg-slate-900 border border-slate-800 rounded-xl p-6">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h2 class="text-base font-semibold text-white">Live Event Stream</h2>
          <p class="text-xs text-slate-400">Real-time dispatched notifications via Kafka</p>
        </div>
        <span class="flex items-center gap-1.5 text-xs text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-full font-medium">
          <span class="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse"></span> Live
        </span>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
          <thead>
            <tr class="border-b border-slate-800 text-slate-400 uppercase tracking-wider text-[10px]">
              <th class="pb-3 font-semibold">Event ID</th>
              <th class="pb-3 font-semibold">Recipient</th>
              <th class="pb-3 font-semibold">Channel</th>
              <th class="pb-3 font-semibold">Status</th>
              <th class="pb-3 font-semibold">Latency</th>
              <th class="pb-3 font-semibold text-right">Timestamp</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-800/60 text-slate-300">
            <tr *ngFor="let item of events" class="hover:bg-slate-800/30 transition">
              <td class="py-3.5 font-mono text-slate-400">{{ item.id }}</td>
              <td class="py-3.5 font-medium text-white">{{ item.recipient }}</td>
              <td class="py-3.5">
                <span class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded bg-slate-800 text-slate-300">
                  <lucide-icon [img]="getChannelIcon(item.channel)" [size]="12"></lucide-icon>
                  {{ item.channel }}
                </span>
              </td>
              <td class="py-3.5">
                <span [class]="item.status === 'DELIVERED' ? 'text-emerald-400 bg-emerald-500/10' : 'text-amber-400 bg-amber-500/10'"
                      class="px-2 py-0.5 rounded font-medium text-[11px]">
                  {{ item.status }}
                </span>
              </td>
              <td class="py-3.5 text-slate-400">{{ item.latency }}</td>
              <td class="py-3.5 text-right text-slate-500">{{ item.time }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class LiveFeedComponent {
  readonly MailIcon = Mail;
  readonly SmsIcon = MessageSquare;
  readonly PushIcon = Bell;

  readonly events = [
    { id: 'evt_9901', recipient: 'user_882@domain.com', channel: 'EMAIL', status: 'DELIVERED', latency: '42ms', time: 'Just now' },
    { id: 'evt_9902', recipient: '+212600112233', channel: 'SMS', status: 'DELIVERED', latency: '128ms', time: '1s ago' },
    { id: 'evt_9903', recipient: 'app_client_device_4', channel: 'PUSH', status: 'QUEUED', latency: '--', time: '3s ago' },
    { id: 'evt_9904', recipient: 'finance_alerts_team', channel: 'EMAIL', status: 'DELIVERED', latency: '58ms', time: '5s ago' }
  ];

  getChannelIcon(channel: string) {
    switch (channel) {
      case 'EMAIL': return this.MailIcon;
      case 'SMS': return this.SmsIcon;
      default: return this.PushIcon;
    }
  }
}
