import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import {
  LucideAngularModule,
  LayoutDashboard,
  Send,
  Sliders,
  Users,
  Activity,
  Settings
} from 'lucide-angular';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, LucideAngularModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
  readonly navItems = [
    { label: 'Overview', path: '/overview', icon: LayoutDashboard },
    { label: 'Rule Simulator', path: '/simulator', icon: Sliders },
    { label: 'Preferences', path: '/preferences', icon: Users },
    { label: 'Live Events', path: '/events', icon: Activity },
    { label: 'Settings', path: '/settings', icon: Settings },
  ];
}
