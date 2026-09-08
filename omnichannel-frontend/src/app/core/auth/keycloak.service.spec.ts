import { TestBed } from '@angular/core/testing';
import { KeycloakService } from './keycloak.service';

/**
 * KeycloakService builds its own real keycloak-js `Keycloak` instance as a
 * private field initializer (`private readonly keycloak = new Keycloak(...)`),
 * using the value imported directly from the `keycloak-js` ES module — not a
 * `window.Keycloak` global. keycloak-js's npm/ESM build never touches
 * `window` (only its standalone `<script>` UMD build does that), so there is
 * nothing at `window.Keycloak` to `spyOn` in a Karma/webpack test run — doing
 * so throws "<spyOn> : Keycloak method does not exist" (or, depending on
 * Jasmine/Karma plumbing, surfaces as a failure on the very next spy call in
 * the same beforeEach, e.g. `init()`) and takes down every test in this file,
 * because it's spying on the wrong object: even a *successful* spy on
 * `window.Keycloak` could never intercept the module-scoped `new Keycloak(...)`
 * call inside the service, since that reference is resolved lexically at
 * import time, not looked up on `window`.
 *
 * The reliable fix already half-present below — swap the service's private
 * `keycloak` field for a fully-controlled mock right after DI creates the
 * service — is kept as the only mocking mechanism. Letting the real
 * `Keycloak` constructor run once during `TestBed.inject()` is harmless:
 * keycloak-js's constructor only stores config, it performs no network call
 * and touches no browser API, so nothing in it can fail in a test
 * environment — the mock instance replaces it before any test body runs.
 */
describe('KeycloakService', () => {
  let service: KeycloakService;
  let mockKeycloakInstance: any;

  beforeEach(() => {
    mockKeycloakInstance = {
      authenticated: false,
      token: '',
      tokenParsed: {},
      init: jasmine.createSpy('init').and.returnValue(Promise.resolve(true)),
      login: jasmine.createSpy('login').and.returnValue(Promise.resolve()),
      logout: jasmine.createSpy('logout').and.returnValue(Promise.resolve()),
      updateToken: jasmine.createSpy('updateToken').and.returnValue(Promise.resolve(true)),
      loadUserProfile: jasmine.createSpy('loadUserProfile').and.returnValue(Promise.resolve({}))
    };

    TestBed.configureTestingModule({});
    service = TestBed.inject(KeycloakService);

    // Inject the mock instance into the service for testing consistency —
    // this is the actual mocking mechanism (see class doc comment above for
    // why spying on window.Keycloak cannot work and was removed).
    (service as any).keycloak = mockKeycloakInstance;
  });

  describe('init', () => {
    it('should mark the service initialized and load the profile when authentication succeeds', async () => {
      mockKeycloakInstance.init.and.callFake(function (this: any) {
        this.authenticated = true;
        return Promise.resolve(true);
      });
      mockKeycloakInstance.loadUserProfile.and.returnValue(
        Promise.resolve({ username: 'jdoe', email: 'jdoe@test.com', firstName: 'Jane', lastName: 'Doe' })
      );

      await service.init();

      expect(service.isLoggedIn()).toBeTrue();
      expect(service.username).toBe('jdoe');
      expect(service.fullName).toBe('Jane Doe');
    });

    it('should redirect to login when init() resolves unauthenticated (defensive branch)', async () => {
      mockKeycloakInstance.init.and.returnValue(Promise.resolve(false));

      await service.init();

      expect(mockKeycloakInstance.login).toHaveBeenCalled();
      expect(service.isLoggedIn()).toBeFalse();
    });

    it('should log and rethrow when init() itself fails (e.g. Keycloak container unreachable)', async () => {
      const error = new Error('connection refused');
      mockKeycloakInstance.init.and.returnValue(Promise.reject(error));
      spyOn(console, 'error');

      await expectAsync(service.init()).toBeRejectedWith(error);
      expect(console.error).toHaveBeenCalled();
    });

    it('should tolerate loadUserProfile() failing without failing init() itself, falling back to token claims', async () => {
      mockKeycloakInstance.init.and.callFake(function (this: any) {
        this.authenticated = true;
        this.tokenParsed = { preferred_username: 'from-token' };
        return Promise.resolve(true);
      });
      mockKeycloakInstance.loadUserProfile.and.returnValue(Promise.reject(new Error('account API down')));
      spyOn(console, 'warn');

      await service.init();

      expect(service.isLoggedIn()).toBeTrue();
      expect(service.username).toBe('from-token');
    });
  });

  describe('getToken', () => {
    it('should resolve to an empty string before init() has completed', async () => {
      expect(await service.getToken()).toBe('');
    });

    it('should refresh the token and return it once initialized', async () => {
      mockKeycloakInstance.init.and.callFake(function (this: any) {
        this.authenticated = true;
        return Promise.resolve(true);
      });
      mockKeycloakInstance.loadUserProfile.and.returnValue(Promise.resolve({}));
      mockKeycloakInstance.updateToken.and.callFake(function (this: any) {
        this.token = 'fresh-jwt';
        return Promise.resolve(true);
      });
      await service.init();

      expect(await service.getToken()).toBe('fresh-jwt');
    });

    it('should return an empty string and trigger a fresh login when the refresh itself fails', async () => {
      mockKeycloakInstance.init.and.callFake(function (this: any) {
        this.authenticated = true;
        return Promise.resolve(true);
      });
      mockKeycloakInstance.loadUserProfile.and.returnValue(Promise.resolve({}));
      mockKeycloakInstance.updateToken.and.returnValue(Promise.reject(new Error('session expired')));
      spyOn(console, 'warn');
      await service.init();

      expect(await service.getToken()).toBe('');
      expect(mockKeycloakInstance.login).toHaveBeenCalled();
    });
  });

  describe('display getters', () => {
    it('should fall back to the token claims when no profile was ever loaded', async () => {
      mockKeycloakInstance.init.and.callFake(function (this: any) {
        this.authenticated = true;
        this.tokenParsed = { preferred_username: 'tokenuser', email: 'token@test.com', given_name: 'Tok', family_name: 'En', realm_access: { roles: ['ADMIN'] } };
        return Promise.resolve(true);
      });
      mockKeycloakInstance.loadUserProfile.and.returnValue(Promise.reject(new Error('down')));
      spyOn(console, 'warn');
      await service.init();

      expect(service.username).toBe('tokenuser');
      expect(service.email).toBe('token@test.com');
      expect(service.firstName).toBe('Tok');
      expect(service.lastName).toBe('En');
      expect(service.fullName).toBe('Tok En');
      expect(service.roles).toEqual(['ADMIN']);
      expect(service.hasRole('ADMIN')).toBeTrue();
      expect(service.hasRole('GUEST')).toBeFalse();
    });

    it('should prefer the loaded profile over token claims when both are present', async () => {
      mockKeycloakInstance.init.and.callFake(function (this: any) {
        this.authenticated = true;
        this.tokenParsed = { preferred_username: 'tokenuser', given_name: 'TokenFirst' };
        return Promise.resolve(true);
      });
      mockKeycloakInstance.loadUserProfile.and.returnValue(Promise.resolve({ username: 'profileuser', firstName: 'ProfileFirst' }));
      await service.init();

      expect(service.username).toBe('profileuser');
      expect(service.firstName).toBe('ProfileFirst');
    });

    it('fullName should fall back to username, then to a neutral label, when no name parts are available', async () => {
      mockKeycloakInstance.init.and.callFake(function (this: any) {
        this.authenticated = true;
        this.tokenParsed = { preferred_username: 'justausername' };
        return Promise.resolve(true);
      });
      mockKeycloakInstance.loadUserProfile.and.returnValue(Promise.resolve({}));
      await service.init();
      expect(service.fullName).toBe('justausername');
    });

    it('fullName should be "Utilisateur" before anything has loaded', () => {
      expect(service.fullName).toBe('Utilisateur');
    });

    it('roles should default to an empty array when there is no tokenParsed at all', () => {
      expect(service.roles).toEqual([]);
      expect(service.hasRole('ANY')).toBeFalse();
    });
  });

  describe('logout', () => {
    it('should call keycloak.logout with the current origin as the redirect URI', () => {
      service.logout();

      expect(mockKeycloakInstance.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });
    });
  });
});
