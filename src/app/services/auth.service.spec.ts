import { TestBed } from '@angular/core/testing';
import { AuthService, UserProfile } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(AuthService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should authenticate user and set profile on loginWithKeycloak', (done) => {
    service.loginWithKeycloak();
    expect(localStorage.getItem('access_token')).toBe('mock_jwt_token_eventflow_2026');

    service.isLoggedIn().subscribe((loggedIn) => {
      expect(loggedIn).toBeTrue();
    });

    service.getProfile().subscribe((profile: UserProfile | null) => {
      expect(profile).not.toBeNull();
      expect(profile?.username).toBe('saad_admin');
      expect(profile?.email).toBe('saad@eventflow.io');
      done();
    });
  });

  it('should clear token and profile on logout', (done) => {
    service.loginWithKeycloak();
    service.logout();
    expect(localStorage.getItem('access_token')).toBeNull();

    service.isLoggedIn().subscribe((loggedIn) => {
      expect(loggedIn).toBeFalse();
    });

    service.getProfile().subscribe((profile: UserProfile | null) => {
      expect(profile).toBeNull();
      done();
    });
  });
});
