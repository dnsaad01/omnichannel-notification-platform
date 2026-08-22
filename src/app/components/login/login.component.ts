import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService, UserRole } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  emailOrUserId: string = 'admin@eventflow.io';
  password: string = 'password123';
  selectedRole: UserRole = 'ADMIN';

  errorMessage: string | null = null;
  isLoading: boolean = false;

  constructor(private authService: AuthService) {}

  selectRole(role: UserRole): void {
    this.selectedRole = role;
    if (role === 'ADMIN') {
      this.emailOrUserId = 'admin@eventflow.io';
    } else {
      this.emailOrUserId = 'usr_1001';
    }
  }

  onSubmit(): void {
    this.errorMessage = null;

    if (!this.emailOrUserId.trim()) {
      this.errorMessage = 'Please enter an Email Address or User ID.';
      return;
    }

    if (!this.password) {
      this.errorMessage = 'Please enter your password.';
      return;
    }

    this.isLoading = true;

    setTimeout(() => {
      const success = this.authService.login({
        emailOrUserId: this.emailOrUserId.trim(),
        password: this.password,
        role: this.selectedRole
      });

      this.isLoading = false;

      if (!success) {
        this.errorMessage = 'Invalid credentials. Please try again.';
      }
    }, 600);
  }
}
