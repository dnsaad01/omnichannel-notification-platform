import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface UserProfile {
  username: string;
  email: string;
  realmRoles: string[];
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private loggedIn$ = new BehaviorSubject<boolean>(false);
  private userProfile$ = new BehaviorSubject<UserProfile | null>(null);

  constructor() {
    const sessionToken = localStorage.getItem('access_token');
    if (sessionToken) {
      this.loggedIn$.next(true);
      this.userProfile$.next({
        username: 'saad_admin',
        email: 'saad@eventflow.io',
        realmRoles: ['ROLE_ADMIN', 'ROLE_OPERATOR']
      });
    }
  }

  isLoggedIn(): Observable<boolean> {
    return this.loggedIn$.asObservable();
  }

  getProfile(): Observable<UserProfile | null> {
    return this.userProfile$.asObservable();
  }

  loginWithKeycloak(): void {
    localStorage.setItem('access_token', 'mock_jwt_token_eventflow_2026');
    this.loggedIn$.next(true);
    this.userProfile$.next({
      username: 'saad_admin',
      email: 'saad@eventflow.io',
      realmRoles: ['ROLE_ADMIN', 'ROLE_OPERATOR']
    });
  }

  logout(): void {
    localStorage.removeItem('access_token');
    this.loggedIn$.next(false);
    this.userProfile$.next(null);
  }
}
