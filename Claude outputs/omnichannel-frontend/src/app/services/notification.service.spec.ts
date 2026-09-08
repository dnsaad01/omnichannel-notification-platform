import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NotificationService } from './notification.service';
import { NotificationRequest } from '../models/notification-request.model';
import { NotificationLogEntry } from '../models/notification-log.model';

/**
 * NotificationFormComponent's own spec already stubs this service with a
 * jasmine spy — this suite is the HTTP-level counterpart, proving the real
 * requests (URL, headers, body, and the getRecentLogs() user->recipient
 * mapping described in this service's own doc comment) are actually correct.
 */
describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;

  const SEND_URL = 'http://localhost:8082/api/v1/notifications/send';
  const LOGS_URL = 'http://localhost:8082/api/dashboard/logs';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should POST to /api/v1/notifications/send with an X-API-KEY header and the request as the body', () => {
    const request: NotificationRequest = { recipientId: 'usr_1001', channel: 'EMAIL', priority: 'HIGH', subject: 'Hi', body: 'Hello' };

    service.sendNotification('secret-key', request).subscribe();

    const req = httpMock.expectOne(SEND_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('X-API-KEY')).toBe('secret-key');
    expect(req.request.headers.get('Content-Type')).toBe('application/json');
    expect(req.request.body).toEqual(request);
    req.flush({ message: 'queued' });
  });

  it('should send an empty string X-API-KEY header rather than omit it when no api key is given', () => {
    service.sendNotification('', { recipientId: 'usr_1001', channel: 'EMAIL' }).subscribe();

    const req = httpMock.expectOne(SEND_URL);
    expect(req.request.headers.get('X-API-KEY')).toBe('');
    req.flush({});
  });

  it('should GET /api/dashboard/logs with the given limit and map user -> recipient on every entry', () => {
    let actual: NotificationLogEntry[] | undefined;
    service.getRecentLogs(5).subscribe(response => (actual = response));

    const req = httpMock.expectOne(r => r.url === LOGS_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('limit')).toBe('5');

    req.flush([
      { id: 'NOTIF-1', user: 'alice@test.com', channel: 'EMAIL', status: 'SENT', time: '2026-01-01T00:00:00' },
      { id: 'NOTIF-2', user: 'bob@test.com', channel: 'SMS', status: 'FAILED', time: '2026-01-02T00:00:00' }
    ]);

    expect(actual).toEqual([
      { id: 'NOTIF-1', recipient: 'alice@test.com', channel: 'EMAIL', status: 'SENT', time: '2026-01-01T00:00:00' },
      { id: 'NOTIF-2', recipient: 'bob@test.com', channel: 'SMS', status: 'FAILED', time: '2026-01-02T00:00:00' }
    ]);
  });

  it('should default the limit to 20 when none is given', () => {
    service.getRecentLogs().subscribe();

    const req = httpMock.expectOne(r => r.url === LOGS_URL);
    expect(req.request.params.get('limit')).toBe('20');
    req.flush([]);
  });
});
