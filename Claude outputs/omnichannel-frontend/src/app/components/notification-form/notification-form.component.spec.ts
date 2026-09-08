import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { NotificationFormComponent } from './notification-form.component';
import { NotificationService } from '../../services/notification.service';
import { ErrorResponse } from '../../models/error-response.model';

/**
 * NotificationService is stubbed with a jasmine spy (same convention as
 * SidebarComponent's spec) rather than HttpClientTesting — this component
 * never talks to HttpClient directly, so there's nothing for
 * HttpTestingController to intercept; NotificationService's own spec is
 * the right place for HTTP-level assertions.
 */
describe('NotificationFormComponent', () => {
  let fixture: ComponentFixture<NotificationFormComponent>;
  let component: NotificationFormComponent;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;

  beforeEach(async () => {
    notificationServiceSpy = jasmine.createSpyObj<NotificationService>('NotificationService', ['sendNotification']);

    await TestBed.configureTestingModule({
      imports: [NotificationFormComponent],
      providers: [{ provide: NotificationService, useValue: notificationServiceSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create with its default form values', () => {
    expect(component).toBeTruthy();
    expect(component.channel).toBe('EMAIL');
    expect(component.priority).toBe('HIGH');
    expect(component.responseLogs).toEqual([]);
  });

  it('should call NotificationService.sendNotification with the current form fields on submit', () => {
    notificationServiceSpy.sendNotification.and.returnValue(of({ message: 'queued' }));
    component.apiKey = 'test-key-123';
    component.recipientId = 'usr_42';
    component.channel = 'SMS';

    component.onSubmit();

    expect(notificationServiceSpy.sendNotification).toHaveBeenCalledTimes(1);
    const [apiKeyArg, requestArg] = notificationServiceSpy.sendNotification.calls.mostRecent().args;
    expect(apiKeyArg).toBe('test-key-123');
    expect(requestArg.recipientId).toBe('usr_42');
    expect(requestArg.channel).toBe('SMS');
  });

  it('should record a success log entry, clear loading, and emit "sent" when the request succeeds', () => {
    notificationServiceSpy.sendNotification.and.returnValue(of({ message: 'Notification request successfully queued for processing' }));
    let sentEmitted = false;
    component.sent.subscribe(() => (sentEmitted = true));

    component.onSubmit();

    expect(component.isLoading).toBeFalse();
    expect(component.latestSuccessMessage).toBe('Notification request successfully queued for processing');
    expect(component.latestError).toBeNull();
    expect(component.responseLogs.length).toBe(1);
    expect(component.responseLogs[0].isSuccess).toBeTrue();
    expect(component.responseLogs[0].statusCode).toBe(202);
    expect(sentEmitted).toBeTrue();
  });

  it('should default the success message when the backend response has no message field', () => {
    notificationServiceSpy.sendNotification.and.returnValue(of({}));

    component.onSubmit();

    expect(component.latestSuccessMessage).toBe('Notification request successfully queued for processing');
  });

  it('should record a structured error log entry when the backend returns a real ErrorResponse body', () => {
    const backendError: ErrorResponse = {
      timestamp: '2026-01-01T00:00:00',
      status: 401,
      error: 'Unauthorized',
      message: 'Invalid API Key',
      path: '/api/v1/notifications/send'
    };
    const httpError = new HttpErrorResponse({ error: backendError, status: 401, statusText: 'Unauthorized' });
    notificationServiceSpy.sendNotification.and.returnValue(throwError(() => httpError));

    component.onSubmit();

    expect(component.isLoading).toBeFalse();
    expect(component.latestSuccessMessage).toBeNull();
    expect(component.latestError).toEqual(backendError);
    expect(component.responseLogs[0].isSuccess).toBeFalse();
    expect(component.responseLogs[0].statusCode).toBe(401);
    expect(component.responseLogs[0].message).toBe('Invalid API Key');
  });

  it('should fall back to a synthesized ErrorResponse when the failure never reached the backend (e.g. a network error)', () => {
    const networkError = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error', error: new ProgressEvent('error') });
    notificationServiceSpy.sendNotification.and.returnValue(throwError(() => networkError));

    component.onSubmit();

    // status 0 is falsy, so the component's `err.status || 500` fallback kicks in.
    expect(component.latestError?.status).toBe(500);
    // Angular's HttpErrorResponse constructor always synthesizes a non-empty
    // `message` itself (e.g. "Http failure response for (unknown url): 0
    // Unknown Error"), so the component's own `err.message || 'An unexpected
    // error occurred'` fallback takes the real HttpErrorResponse message here
    // — the hardcoded string is only reached if something other than a real
    // HttpErrorResponse (with an empty message) is thrown. Assert against the
    // actual message Angular produced, not the unreachable-in-practice string.
    expect(component.latestError?.message).toBe(networkError.message);
    expect(component.responseLogs[0].isSuccess).toBeFalse();
  });

  it('should use the hardcoded fallback message when a thrown error has no message of its own', () => {
    // A bare, non-HttpErrorResponse-shaped rejection — e.g. some upstream
    // code throwing a plain object — is the one case where `err.message` is
    // actually falsy, so this exercises the hardcoded fallback string itself.
    notificationServiceSpy.sendNotification.and.returnValue(throwError(() => ({ status: 0 })));

    component.onSubmit();

    expect(component.latestError?.status).toBe(500);
    expect(component.latestError?.message).toBe('An unexpected error occurred');
  });

  it('should not emit "sent" when the request fails', () => {
    notificationServiceSpy.sendNotification.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    let sentEmitted = false;
    component.sent.subscribe(() => (sentEmitted = true));

    component.onSubmit();

    expect(sentEmitted).toBeFalse();
  });

  it('clearLogs should reset the logs, success message, and error state', () => {
    notificationServiceSpy.sendNotification.and.returnValue(of({ message: 'ok' }));
    component.onSubmit();
    expect(component.responseLogs.length).toBe(1);

    component.clearLogs();

    expect(component.responseLogs).toEqual([]);
    expect(component.latestSuccessMessage).toBeNull();
    expect(component.latestError).toBeNull();
  });
});
