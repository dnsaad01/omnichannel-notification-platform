import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { KeycloakService } from '../auth/keycloak.service';

/** Every service in this app (workflow.service.ts, notification.service.ts,
 *  statistics.service.ts, monitoring.service.ts, simulator.service.ts, ...)
 *  hardcodes this same origin — there's no environment.ts yet, so this is
 *  the one place that origin is duplicated for matching purposes. Keeps the
 *  interceptor from ever attaching a Bearer token to a request that isn't
 *  going to our own backend (e.g. Keycloak's own /realms/... calls, which
 *  keycloak-js issues directly and which must NOT carry this token). */
const API_BASE_URL = 'http://localhost:8082';

/**
 * Functional interceptor (registered via provideHttpClient(withInterceptors([...]))
 * in app.config.ts). Attaches `Authorization: Bearer <token>` to every
 * request bound for our backend, refreshing the token first via
 * KeycloakService.getToken() if it's close to expiry.
 *
 * This is what SecurityConfig's new .oauth2ResourceServer(...) on the
 * backend now actually requires for every endpoint that isn't in
 * PUBLIC_PATTERNS — without this, every protected call from the app would
 * start failing with 401 the moment the backend change ships.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(API_BASE_URL)) {
    return next(req);
  }

  const keycloakService = inject(KeycloakService);

  return from(keycloakService.getToken()).pipe(
    switchMap(token => {
      const authorizedReq = token
        ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
        : req;
      return next(authorizedReq);
    })
  );
};
