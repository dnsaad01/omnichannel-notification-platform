import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { DashboardService } from '../../services/dashboard.service';
import { SimulatorService } from '../../services/simulator.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  private dashboardService = inject(DashboardService);
  private simulatorService = inject(SimulatorService);

  stats = {
    totalSent: '24,580',
    successRate: '96.8%',
    activeChannels: '4'
  };

  /** Derived from recentLogs — replaces the old Cost Engine tile now that module is gone. */
  get failedDeliveries(): number {
    return this.recentLogs.filter(l => l.status === 'Failed').length;
  }

  recentLogs = [
    { id: 'NOTIF-1025', user: 'usr_843@gmail.com', channel: 'EMAIL', status: 'Delivered', time: '2 mins ago' },
    { id: 'NOTIF-1024', user: '+212600112233', channel: 'SMS', status: 'Pending', time: '5 mins ago' },
    { id: 'NOTIF-1023', user: 'usr_991@corp.io', channel: 'PUSH', status: 'Failed', time: '12 mins ago' },
  ];

  isRefreshing: boolean = false;
  toastMessage: string | null = null;

  ngOnInit() {
    this.fetchDashboardData();
  }

  fetchDashboardData() {
    this.isRefreshing = true;
    this.dashboardService.getStats().subscribe({
      next: (data) => {
        if (data) {
          this.stats = data;
        }
        this.isRefreshing = false;
      },
      error: () => {
        this.isRefreshing = false;
        console.warn('Using local fallback stats');
      }
    });

    this.dashboardService.getRecentLogs().subscribe({
      next: (logs) => {
        if (logs && logs.length > 0) {
          this.recentLogs = logs;
        }
      },
      error: () => {
        console.warn('Using local fallback logs');
      }
    });
  }

  refreshDashboard() {
    this.fetchDashboardData();
    this.showToast('Données du Dashboard actualisées !', 'refresh-cw');
  }

  triggerSimulatedDispatch() {
    this.simulatorService.sendSingleEvent().subscribe({
      next: (res) => {
        this.showToast(`Dispatched simulated notification ${res.event?.eventId || ''}!`, 'zap');
        this.fetchDashboardData();
      }
    });
  }

  toastIcon: string = 'circle-check-big';

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
