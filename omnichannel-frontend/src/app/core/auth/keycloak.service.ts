import { Injectable } from '@angular/core';
import Keycloak, { KeycloakProfile } from 'keycloak-js';

/**
 * Thin wrapper around keycloak-js — this is the whole "login redirect /
 * token storage" story for the frontend, and deliberately so:
 *
 *  - Login redirect: init() is called once, at app bootstrap, with
 *    `onLoad: 'login-required'`. keycloak-js itself redirects the browser
 *    to Keycloak's hosted login page before Angular renders anything, and
 *    handles the redirect back (Authorization Code + PKCE, matching
 *    frontend-client's `publicClient: true` / `standardFlowEnabled: true`
 *    config in realm-export.json) — there's no manual /callback route or
 *    guard to write.
 *  - Token storage: keycloak-js keeps the access/refresh token in memory
 *    inside the Keycloak instance, not in localStorage/sessionStorage — it
 *    survives the redirect via Keycloak's own internal state, and a page
 *    refresh re-runs init(), which silently re-authenticates using the
 *    Keycloak session cookie rather than re-prompting for credentials. This
 *    avoids the XSS-exposure problem of hand-rolling token storage.
 *  - Refresh: getToken() calls updateToken() before every use, so a token
 *    that's about to expire is silently refreshed; the interceptor never
 *    has to know this happens.
 *  - Profile: loadProfile() calls keycloak-js's loadUserProfile(), which
 *    hits Keycloak's Account REST API (/realms/omnichannel-realm/account)
 *    rather than reading claims off the access token. This is the more
 *    reliable source for display fields — token claims depend on which
 *    client scopes are attached to frontend-client (profile/email are
 *    Keycloak defaults, but nothing guarantees they stay attached), while
 *    the Account endpoint always returns the user's actual stored
 *    username/email/firstName/lastName regardless of scope config. Every
 *    getter below still falls back to the token claim if the profile call
 *    ever fails, so a real name/email keeps showing even if that one extra
 *    request breaks.
 */
@Injectable({ providedIn: 'root' })
export class KeycloakService {
  private readonly keycloak = new Keycloak({
    url: 'http://localhost:8081',
    realm: 'omnichannel-realm',
    clientId: 'frontend-client'
  });

  private initialized = false;
  private profile: KeycloakProfile | null = null;

  /** Called once from app.config.ts via provideAppInitializer — the app
   *  does not finish bootstrapping until this resolves, so every component
   *  can assume a valid session (and, best-effort, a loaded profile)
   *  already exists. */
  async init(): Promise<void> {
    try {
      const authenticated = await this.keycloak.init({
        onLoad: 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false
      });

      this.initialized = true;

      if (!authenticated) {
        // Defensive only: with onLoad 'login-required', init() resolving
        // at all means the user is authenticated. This branch exists so a
        // future onLoad change (e.g. 'check-sso') doesn't silently let an
        // unauthenticated session through.
        await this.keycloak.login();
        return;
      }

      await this.loadProfile();
    } catch (err) {
      console.error(
        '[KeycloakService] init() failed — is the Keycloak container running on http://localhost:8081 ' +
        'and is the omnichannel-realm realm imported? See docker-compose.yml (root).',
        err
      );
      throw err;
    }
  }

  /** Fetches the real Keycloak user profile (username/email/firstName/
   *  lastName) via the Account REST API. Deliberately non-fatal: a failure
   *  here (e.g. the account endpoint being unreachable) must not break
   *  app bootstrap, since the token-claim fallbacks in the getters below
   *  are usually enough to render a reasonable name/email anyway. */
  private async loadProfile(): Promise<void> {
    try {
      this.profile = await this.keycloak.loadUserProfile();
    } catch (err) {
      console.warn('[KeycloakService] loadUserProfile() failed, falling back to token claims', err);
      this.profile = null;
    }
  }

  /** Resolves to a currently-valid access token, refreshing it first if it
   *  expires within 30s. Resolves to '' if called before init() completes,
   *  or if the refresh itself fails (session expired outright) — in the
   *  latter case a fresh login redirect is triggered rather than sending a
   *  request with a dead token. */
  async getToken(): Promise<string> {
    if (!this.initialized) {
      return '';
    }

    try {
      await this.keycloak.updateToken(30);
    } catch (err) {
      console.warn('[KeycloakService] token refresh failed, redirecting to login', err);
      await this.keycloak.login();
      return '';
    }

    return this.keycloak.token ?? '';
  }

  isLoggedIn(): boolean {
    return this.initialized && !!this.keycloak.authenticated;
  }

  /** Account-service username, falling back to the token's
   *  preferred_username claim if loadUserProfile() never succeeded. */
  get username(): string | undefined {
    return this.profile?.username ?? this.keycloak.tokenParsed?.['preferred_username'];
  }

  get email(): string | undefined {
    return this.profile?.email ?? this.keycloak.tokenParsed?.['email'];
  }

  get firstName(): string | undefined {
    return this.profile?.firstName ?? this.keycloak.tokenParsed?.['given_name'];
  }

  get lastName(): string | undefined {
    return this.profile?.lastName ?? this.keycloak.tokenParsed?.['family_name'];
  }

  /** "First Last", trimmed. Falls back to username, then to a neutral
   *  label — a real Keycloak user always has at least a username, so
   *  'Utilisateur' is only ever seen before init() has resolved. */
  get fullName(): string {
    const combined = [this.firstName, this.lastName].filter(Boolean).join(' ').trim();
    return combined || this.username || 'Utilisateur';
  }

  get roles(): string[] {
    return this.keycloak.tokenParsed?.realm_access?.roles ?? [];
  }

  hasRole(role: string): boolean {
    return this.roles.includes(role);
  }

  logout(): void {
    this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
