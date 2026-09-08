import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { KeycloakService } from '../auth/keycloak.service';

/**
 * authInterceptor is a functional interceptor (HttpInterceptorFn), not a
 * class — the same "no class, just a function that calls inject()" shape
 * a functional route guard (CanActivateFn) has. It's registered here via
 * provideHttpClient(withInterceptors([...])), the exact way app.config.ts
 * wires it into the real app, and driven through a real HttpClient +
 * HttpTestingController rather than calling the function directly — that
 * exercises `next()` for real and is what actually proves the header ends
 * up on the outgoing request, not just on whatever req.clone() happened to
 * return in isolation.
 *
 * KeycloakService.getToken() is async, and the interceptor wraps it in
 * from(...).pipe(switchMap(...)), so every request here needs fakeAsync +
 * tick() before HttpTestingController sees it — without tick(), the
 * Promise inside getToken() never gets a chance to resolve and
 * expectOne(...) fails with "no request found".
 */
describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let keycloakServiceSpy: jasmine.SpyObj<KeycloakService>;

  beforeEach(() => {
    keycloakServiceSpy = jasmine.createSpyObj<KeycloakService>('KeycloakService', ['getToken']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: KeycloakService, useValue: keycloakServiceSpy }
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should attach "Authorization: Bearer <token>" to a request bound for our own backend', fakeAsync(() => {
    keycloakServiceSpy.getToken.and.returnValue(Promise.resolve('real-jwt-token'));

    httpClient.get('http://localhost:8082/api/workflows').subscribe();
    tick();

    const req = httpMock.expectOne('http://localhost:8082/api/workflows');
    expect(req.request.headers.get('Authorization')).toBe('Bearer real-jwt-token');
    req.flush({});
  }));

  it('should never call KeycloakService or attach a header for a request to a different origin (e.g. Keycloak itself)', fakeAsync(() => {
    const keycloakUrl = 'http://localhost:8081/realms/omnichannel-realm/.well-known/openid-configuration';

    httpClient.get(keycloakUrl).subscribe();
    tick();

    const req = httpMock.expectOne(keycloakUrl);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    expect(keycloakServiceSpy.getToken).not.toHaveBeenCalled();
    req.flush({});
  }));

  it('should forward the request without an Authorization header when getToken resolves empty (not yet initialized, or refresh failed)', fakeAsync(() => {
    // See KeycloakService#getToken's own doc comment: '' means either
    // init() hasn't completed yet, or a refresh failure already triggered
    // a fresh login redirect — either way there's no valid token to attach.
    keycloakServiceSpy.getToken.and.returnValue(Promise.resolve(''));

    httpClient.get('http://localhost:8082/api/workflows').subscribe();
    tick();

    const req = httpMock.expectOne('http://localhost:8082/api/workflows');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  }));

  it('should still refresh and attach the token for a POST request with a body', fakeAsync(() => {
    keycloakServiceSpy.getToken.and.returnValue(Promise.resolve('another-token'));

    httpClient.post('http://localhost:8082/api/business-events/publish', { eventType: 'CART_ABANDONED' }).subscribe();
    tick();

    const req = httpMock.expectOne('http://localhost:8082/api/business-events/publish');
    expect(req.request.headers.get('Authorization')).toBe('Bearer another-token');
    expect(req.request.body).toEqual({ eventType: 'CART_ABANDONED' });
    req.flush({});
  }));
});
