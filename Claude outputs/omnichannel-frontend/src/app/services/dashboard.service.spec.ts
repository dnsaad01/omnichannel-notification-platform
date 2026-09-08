import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let httpMock: HttpTestingController;

  const BASE_URL = 'http://localhost:8082/api/dashboard';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(DashboardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET dashboard stats from /api/dashboard/stats', () => {
    let actual: any;
    service.getStats().subscribe(response => (actual = response));

    const req = httpMock.expectOne(`${BASE_URL}/stats`);
    expect(req.request.method).toBe('GET');
    const stats = { totalSent: 1200, totalFailed: 8 };
    req.flush(stats);

    expect(actual).toEqual(stats);
  });

  it('should GET recent logs from /api/dashboard/logs', () => {
    let actual: any;
    service.getRecentLogs().subscribe(response => (actual = response));

    const req = httpMock.expectOne(`${BASE_URL}/logs`);
    expect(req.request.method).toBe('GET');
    const logs = [{ id: 'NOTIF-1', user: 'user@test.com', channel: 'EMAIL', status: 'SENT', time: '2026-01-01T00:00:00' }];
    req.flush(logs);

    expect(actual).toEqual(logs);
  });

  it('should propagate a backend error from getStats to the caller', () => {
    let captured: any;
    service.getStats().subscribe({
      next: () => fail('expected an error, not a success'),
      error: err => (captured = err.status)
    });

    httpMock.expectOne(`${BASE_URL}/stats`).flush('Internal error', { status: 500, statusText: 'Internal Server Error' });

    expect(captured).toBe(500);
  });
});
