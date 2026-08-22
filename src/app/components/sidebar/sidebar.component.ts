import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export type NavTab = 'analytics' | 'dispatch' | 'templates' | 'preferences' | 'dlq' | 'optimization';

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
    { id: 'analytics', label: 'Analytics', icon: '📊' },
    { id: 'dispatch', label: 'Notification Dispatch', icon: '🚀' },
    { id: 'templates', label: 'Channels & Templates', icon: '💬' },
    { id: 'dlq', label: 'DLQ Inspector', icon: '🚨' },
    { id: 'optimization', label: 'Optimization Flow', icon: '⚡' },
    { id: 'preferences', label: 'Recipient Preferences', icon: '⚙️' }
  ];

  selectTab(tab: NavTab): void {
    this.activeTab = tab;
    this.tabChange.emit(tab);
  }

  logout(): void {
    alert('Logged out from EventFlow Platform.');
  }
}
