import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const adminGuard: CanActivateFn = (_route, _state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn() && authService.hasRole('ADMIN')) {
    return true;
  }

  if (authService.isLoggedIn() && authService.hasRole('USER')) {
    router.navigate(['/user']);
    return false;
  }

  router.navigate(['/login']);
  return false;
};

export const userGuard: CanActivateFn = (_route, _state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn() && authService.hasRole('USER')) {
    return true;
  }

  if (authService.isLoggedIn() && authService.hasRole('ADMIN')) {
    router.navigate(['/admin']);
    return false;
  }

  router.navigate(['/login']);
  return false;
};
