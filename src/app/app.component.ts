import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationFormComponent } from './components/notification-form/notification-form.component';
import { PreferencesComponent } from './components/preferences/preferences.component';
import { RuleSimulatorComponent } from './components/rule-simulator/rule-simulator.component';
import { LoginComponent } from './components/login/login.component';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    NotificationFormComponent,
    PreferencesComponent,
    RuleSimulatorComponent,
    LoginComponent
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  activeTab: 'dispatch' | 'preferences' | 'simulator' | 'login' = 'dispatch';

  constructor(public authService: AuthService) {}

  setActiveTab(tab: 'dispatch' | 'preferences' | 'simulator' | 'login'): void {
    this.activeTab = tab;
  }
}
