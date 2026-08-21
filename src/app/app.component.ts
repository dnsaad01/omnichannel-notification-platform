import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationFormComponent } from './components/notification-form/notification-form.component';
import { UserPreferencesComponent } from './components/user-preferences/user-preferences.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, NotificationFormComponent, UserPreferencesComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'omnichannel-frontend';
  activeTab: 'dispatch' | 'preferences' = 'dispatch';

  setActiveTab(tab: 'dispatch' | 'preferences'): void {
    this.activeTab = tab;
  }
}
