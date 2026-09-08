import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { KeycloakService } from './keycloak.service';

/**
 * KeycloakService constructs its own `new Keycloak(...)` internally (see
 * that field's own doc comment: token storage deliberately lives inside
 * keycloak-js's instance, not something this app manages) — there's no DI
 * seam to substitute a mock instance through. Instead, every test spies on
 * Keycloak.prototype methods (init/login/logout/updateToken/loadUserProfile)
 * and, where a test needs to simulate what a *real* successful call would
 * have set on the instance (authenticated/token/tokenParsed — real fields
 * keycloak-js itself populates, not something KeycloakService writes), uses
 * a spy callFake bound to `this` to set them, exactly like the real library
 * would. This works the same whether Keycloak is this sandbox's local
 * ngx-vflow-style stand-in or the real npm package — only its prototype
 * shape matters here, not its implementation.
 */
describe('KeycloakService', () => {
  let service: KeycloakService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(KeycloakService);
  });

  afterEach(() => {
    // Reset every spy so a later test never inherits an earlier test's
    // callFake (prototype spies are global, not per-instance).
    const proto = Keycloak.prototype as any;
    ['init', 'login', 'logout', 'updateToken', 'loadUserProfile'].forEach(m => {
      if (proto[m] && proto[m].and) {
        proto[m].and.stub();
      }
    });
  });

  describe('init', () => {
    it('should mark the service initialized and load the profile when authentication succeeds', async () => {
      spyOn(Keycloak.prototype, 'init').and.callFake(function (this: any) {
        this.authenticated = true;
        return Promise.resolve(true);
      });
      spyOn(Keycloak.prototype, 'loadUserProfile').and.returnValue(
        Promise.resolve({ username: 'jdoe', email: 'jdoe@test.com', firstName: 'Jane', lastName: 'Doe' })
      );

      await service.init();

      expect(service.isLoggedIn()).toBeTrue();
      expect(service.username).toBe('jdoe');
      expect(service.fullName).toBe('Jane Doe');
    });

    it('should redirect to login when init() resolves unauthenticated (defensive branch)', async () => {
      spyOn(Keycloak.prototype, 'init').and.returnValue(Promise.resolve(false));
      const loginSpy = spyOn(Keycloak.prototype, 'login').and.returnValue(Promise.resolve());

      await service.init();

      expect(loginSpy).toHaveBeenCalled();
      expect(service.isLoggedIn()).toBeFalse();
    });

    it('should log and rethrow when init() itself fails (e.g. Keycloak container unreachable)', async () => {
      const error = new Error('connection refused');
      spyOn(Keycloak.prototype, 'init').and.returnValue(Promise.reject(error));
      spyOn(console, 'error');

      await expectAsync(service.init()).toBeRejectedWith(error);
      expect(console.error).toHaveBeenCalled();
    });

    it('should tolerate loadUserProfile() failing without failing init() itself, falling back to token claims', async () => {
      spyOn(Keycloak.prototype, 'init').and.callFake(function (this: any) {
        this.authenticated = true;
        this.tokenParsed = { preferred_username: 'from-token' };
        return Promise.resolve(true);
      });
      spyOn(Keycloak.prototype, 'loadUserProfile').and.returnValue(Promise.reject(new Error('account API down')));
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
      spyOn(Keycloak.prototype, 'init').and.callFake(function (this: any) {
        this.authenticated = true;
        return Promise.resolve(true);
      });
      spyOn(Keycloak.prototype, 'loadUserProfile').and.returnValue(Promise.resolve({}));
      spyOn(Keycloak.prototype, 'updateToken').and.callFake(function (this: any) {
        this.token = 'fresh-jwt';
        return Promise.resolve(true);
      });
      await service.init();

      expect(await service.getToken()).toBe('fresh-jwt');
    });

    it('should return an empty string and trigger a fresh login when the refresh itself fails', async () => {
      spyOn(Keycloak.prototype, 'init').and.callFake(function (this: any) {
        this.authenticated = true;
        return Promise.resolve(true);
      });
      spyOn(Keycloak.prototype, 'loadUserProfile').and.returnValue(Promise.resolve({}));
      spyOn(Keycloak.prototype, 'updateToken').and.returnValue(Promise.reject(new Error('session expired')));
      const loginSpy = spyOn(Keycloak.prototype, 'login').and.returnValue(Promise.resolve());
      spyOn(console, 'warn');
      await service.init();

      expect(await service.getToken()).toBe('');
      expect(loginSpy).toHaveBeenCalled();
    });
  });

  describe('display getters', () => {
    it('should fall back to the token claims when no profile was ever loaded', async () => {
      spyOn(Keycloak.prototype, 'init').and.callFake(function (this: any) {
        this.authenticated = true;
        this.tokenParsed = { preferred_username: 'tokenuser', email: 'token@test.com', given_name: 'Tok', family_name: 'En', realm_access: { roles: ['ADMIN'] } };
        return Promise.resolve(true);
      });
      spyOn(Keycloak.prototype, 'loadUserProfile').and.returnValue(Promise.reject(new Error('down')));
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
      spyOn(Keycloak.prototype, 'init').and.callFake(function (this: any) {
        this.authenticated = true;
        this.tokenParsed = { preferred_username: 'tokenuser', given_name: 'TokenFirst' };
        return Promise.resolve(true);
      });
      spyOn(Keycloak.prototype, 'loadUserProfile').and.returnValue(Promise.resolve({ username: 'profileuser', firstName: 'ProfileFirst' }));
      await service.init();

      expect(service.username).toBe('profileuser');
      expect(service.firstName).toBe('ProfileFirst');
    });

    it('fullName should fall back to username, then to a neutral label, when no name parts are available', async () => {
      spyOn(Keycloak.prototype, 'init').and.callFake(function (this: any) {
        this.authenticated = true;
        this.tokenParsed = { preferred_username: 'justausername' };
        return Promise.resolve(true);
      });
      spyOn(Keycloak.prototype, 'loadUserProfile').and.returnValue(Promise.resolve({}));
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
      const logoutSpy = spyOn(Keycloak.prototype, 'logout').and.returnValue(Promise.resolve());

      service.logout();

      expect(logoutSpy).toHaveBeenCalledWith({ redirectUri: window.location.origin });
    });
  });
});
