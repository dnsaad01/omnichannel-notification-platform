import { Injectable, signal, computed } from '@angular/core';
import { Router } from '@angular/router';

export type UserRole = 'ADMIN' | 'USER';

export interface User {
  userId: string;
  email: string;
  name: string;
  role: UserRole;
  token?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly STORAGE_KEY = 'eventflow_auth_user';

  // Angular Signal State
  currentUser = signal<User | null>(this.getUserFromStorage());
  isLoggedIn = computed(() => !!this.currentUser());
  userRole = computed(() => this.currentUser()?.role || null);

  constructor(private router: Router) {}

  login(credentials: { emailOrUserId: string; password: string; role: UserRole }): boolean {
    if (!credentials.emailOrUserId || !credentials.password) {
      return false;
    }

    const role = credentials.role || 'ADMIN';
    const user: User = {
      userId: credentials.emailOrUserId.startsWith('usr_') ? credentials.emailOrUserId : `usr_${Math.floor(1000 + Math.random() * 9000)}`,
      email: credentials.emailOrUserId.includes('@') ? credentials.emailOrUserId : `${credentials.emailOrUserId}@eventflow.io`,
      name: role === 'ADMIN' ? 'Admin User' : 'Recipient User',
      role: role,
      token: 'jwt_mock_token_' + Math.random().toString(36).substring(2)
    };

    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(user));
    this.currentUser.set(user);

    if (role === 'ADMIN') {
      this.router.navigate(['/admin']);
    } else {
      this.router.navigate(['/user']);
    }

    return true;
  }

  logout(): void {
    localStorage.removeItem(this.STORAGE_KEY);
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  hasRole(requiredRole: UserRole): boolean {
    const user = this.currentUser();
    return !!user && user.role === requiredRole;
  }

  private getUserFromStorage(): User | null {
    try {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  }
}
