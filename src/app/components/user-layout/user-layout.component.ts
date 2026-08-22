import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserPreferencesComponent } from '../user-preferences/user-preferences.component';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-user-layout',
  standalone: true,
  imports: [CommonModule, UserPreferencesComponent],
  templateUrl: './user-layout.component.html',
  styleUrl: './user-layout.component.scss'
})
export class UserLayoutComponent {
  constructor(public authService: AuthService) {}

  logout(): void {
    this.authService.logout();
  }
}
