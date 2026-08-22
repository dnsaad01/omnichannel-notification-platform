import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SidebarComponent, NavTab } from './components/sidebar/sidebar.component';
import { NotificationFormComponent } from './components/notification-form/notification-form.component';
import { UserPreferencesComponent } from './components/user-preferences/user-preferences.component';
import { AnalyticsOverviewComponent } from './components/analytics-overview/analytics-overview.component';
import { DlqInspectorComponent } from './components/dlq-inspector/dlq-inspector.component';
import { EngagementOptimizerComponent } from './components/engagement-optimizer/engagement-optimizer.component';

@Component({
  selector: 'app-root',
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
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'EventFlow Platform';
  activeTab: NavTab = 'dispatch';

  onTabChange(tab: NavTab): void {
    this.activeTab = tab;
  }
}
