import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SidebarComponent, NavTab } from '../sidebar/sidebar.component';
import { NotificationFormComponent } from '../notification-form/notification-form.component';
import { UserPreferencesComponent } from '../user-preferences/user-preferences.component';
import { AnalyticsOverviewComponent } from '../analytics-overview/analytics-overview.component';
import { DlqInspectorComponent } from '../dlq-inspector/dlq-inspector.component';
import { EngagementOptimizerComponent } from '../engagement-optimizer/engagement-optimizer.component';

const VIEW_TITLES: Record<NavTab, string> = {
  analytics: 'Analytics Overview',
  dispatch: 'Notification Dispatcher',
  templates: 'Channels & Fallback Rules',
  dlq: 'DLQ Inspector & Recovery',
  optimization: 'Engagement & Sandbox',
  preferences: 'Recipient Directory'
};

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [
    CommonModule,
    SidebarComponent,
    NotificationFormComponent,
    UserPreferencesComponent,
    AnalyticsOverviewComponent,
    DlqInspectorComponent,
    EngagementOptimizerComponent
  ],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss'
})
export class AdminLayoutComponent {
  activeTab: NavTab = 'analytics';

  get viewTitle(): string {
    return VIEW_TITLES[this.activeTab];
  }

  onTabChange(tab: NavTab): void {
    this.activeTab = tab;
  }
}
