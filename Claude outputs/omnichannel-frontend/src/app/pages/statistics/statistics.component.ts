import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StatisticsService } from '../../services/statistics.service';
import { ChannelStatistics } from '../../models/statistics.model';

@Component({
  selector: 'app-statistics',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './statistics.component.html'
})
export class StatisticsComponent implements OnInit {
  private statisticsService = inject(StatisticsService);

  /** Real data from GET /api/dashboard/statistics — starts as zeroed
   *  placeholders (not the old hardcoded 24,580-style mock numbers) so
   *  there's nothing fabricated on screen even for the instant before the
   *  first real response lands. */
  globalStats = {
    totalSent: '—',
    deliveryRate: '—',
    openRate: '—',
    clickRate: '—'
  };

  channels: ChannelStatistics[] = [];

  isLoading = false;
  loadError: string | null = null;

  ngOnInit() {
    this.fetchStatistics();
  }

  fetchStatistics() {
    this.isLoading = true;
    this.loadError = null;

    this.statisticsService.getStatistics().subscribe({
      next: (stats) => {
        this.isLoading = false;
        this.globalStats = {
          totalSent: stats.totalSent,
          deliveryRate: stats.deliveryRate,
          openRate: stats.openRate,
          clickRate: stats.clickRate
        };
        this.channels = stats.channels;
      },
      error: (err) => {
        this.isLoading = false;
        this.loadError = err?.error?.message || 'Impossible de charger les statistiques.';
      }
    });
  }
}
