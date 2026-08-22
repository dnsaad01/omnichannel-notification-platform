import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

export type NavTab = 'analytics' | 'dispatch' | 'templates' | 'dlq' | 'optimization' | 'preferences';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
  @Input() activeTab: NavTab = 'dispatch';
  @Output() tabChange = new EventEmitter<NavTab>();

  navItems: { id: NavTab; label: string; icon: string }[] = [
    { id: 'analytics', label: 'Analytics Overview', icon: '📊' },
    { id: 'dispatch', label: 'Notification Dispatcher', icon: '⚡' },
    { id: 'templates', label: 'Channels & Fallback Rules', icon: '🔀' },
    { id: 'dlq', label: 'DLQ Inspector & Recovery', icon: '🛠️' },
    { id: 'optimization', label: 'Engagement & Sandbox', icon: '📈' },
    { id: 'preferences', label: 'Recipient Directory', icon: '👥' }
  ];

  constructor(public authService: AuthService) {}

  selectTab(tab: NavTab): void {
    this.activeTab = tab;
    this.tabChange.emit(tab);
  }

  logout(): void {
    this.authService.logout();
  }
}
