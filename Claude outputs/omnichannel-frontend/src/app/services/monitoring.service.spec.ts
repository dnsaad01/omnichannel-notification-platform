import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MonitoringService } from './monitoring.service';
import { InfrastructureHealthResponse } from '../models/monitoring.model';

/**
 * provideHttpClientTesting() intercepts every request made through
 * HttpClient during the test — nothing here hits a real backend, so this
 * runs the same whether or not ingestion-service is actually up.
 * HttpTestingController.verify() in afterEach fails the test if the
 * service ever issues a request nobody asserted on, which catches an
 * accidental extra call as loudly as a missing one.
 */
describe('MonitoringService', () => {
  let service: MonitoringService;
  let httpMock: HttpTestingController;

  const HEALTH_URL = 'http://localhost:8082/api/monitoring/health';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(MonitoringService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET /api/monitoring/health and return the response unchanged', () => {
    const mockResponse: InfrastructureHealthResponse = {
      healthy: true,
      database: { up: true, message: 'Operational (PostgreSQL)' },
      kafka: { up: true, message: 'OK (1 broker(s))' },
      redis: { up: true, message: 'Connected' }
    };

    let actual: InfrastructureHealthResponse | undefined;
    service.getHealth().subscribe(response => (actual = response));

    const req = httpMock.expectOne(HEALTH_URL);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(actual).toEqual(mockResponse);
  });

  it('should surface a DOWN database status exactly as returned by the backend, not swallow it', () => {
    // Regression case for the original bug report this endpoint was built
    // to fix: a down Postgres container must show up as healthy: false /
    // database.up: false all the way through to whatever calls this
    // service, not get normalized away into a false "stable" reading.
    const degraded: InfrastructureHealthResponse = {
      healthy: false,
      database: { up: false, message: 'DOWN: Connection refused' },
      kafka: { up: true, message: 'OK (1 broker(s))' },
      redis: { up: true, message: 'Connected' }
    };

    let actual: InfrastructureHealthResponse | undefined;
    service.getHealth().subscribe(response => (actual = response));

    httpMock.expectOne(HEALTH_URL).flush(degraded);

    expect(actual?.healthy).toBeFalse();
    expect(actual?.database.up).toBeFalse();
    expect(actual?.database.message).toBe('DOWN: Connection refused');
  });
});
