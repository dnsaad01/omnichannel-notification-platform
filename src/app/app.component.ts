import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationFormComponent } from './components/notification-form/notification-form.component';
import { PreferencesComponent } from './components/preferences/preferences.component';
import { RuleSimulatorComponent } from './components/rule-simulator/rule-simulator.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    NotificationFormComponent,
    PreferencesComponent,
    RuleSimulatorComponent
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  activeTab: 'dispatch' | 'preferences' | 'simulator' = 'dispatch';

  setActiveTab(tab: 'dispatch' | 'preferences' | 'simulator'): void {
    this.activeTab = tab;
  }
}
