import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { KpiCardComponent } from './components/kpi-card/kpi-card.component';
import { LiveFeedComponent } from './components/live-feed/live-feed.component';
import { Send, CheckCheck, Zap, AlertCircle } from 'lucide-angular';

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [CommonModule, KpiCardComponent, LiveFeedComponent],
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.scss'
})
export class OverviewComponent {
  readonly SendIcon = Send;
  readonly SuccessIcon = CheckCheck;
  readonly ZapIcon = Zap;
  readonly ErrorIcon = AlertCircle;
}
