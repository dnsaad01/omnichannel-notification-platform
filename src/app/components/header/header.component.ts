import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Bell, Search, User } from 'lucide-angular';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss'
})
export class HeaderComponent {
  readonly BellIcon = Bell;
  readonly SearchIcon = Search;
  readonly UserIcon = User;
}
