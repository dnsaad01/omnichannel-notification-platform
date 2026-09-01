import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  isLoading: boolean = false;

  constructor(public authService: AuthService) {}

  initiateKeycloakLogin(): void {
    this.isLoading = true;
    setTimeout(() => {
      this.authService.loginWithKeycloak();
      this.isLoading = false;
    }, 500);
  }
}
