import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StatisticsService } from './statistics.service';
import { StatisticsResponse } from '../models/statistics.model';

describe('StatisticsService', () => {
  let service: StatisticsService;
  let httpMock: HttpTestingController;

  const URL = 'http://localhost:8082/api/dashboard/statistics';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(StatisticsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET /api/dashboard/statistics and return the response as-is', () => {
    const response: StatisticsResponse = {
      totalSent: '1,204',
      deliveryRate: '98.2%',
      openRate: '41.5%',
      clickRate: 'Non suivi',
      channels: [{ name: 'EMAIL', sent: '900', delivered: '890', rate: '98.9%' }]
    };

    let actual: StatisticsResponse | undefined;
    service.getStatistics().subscribe(r => (actual = r));

    const req = httpMock.expectOne(URL);
    expect(req.request.method).toBe('GET');
    req.flush(response);

    expect(actual).toEqual(response);
  });

  it('should propagate a backend error to the caller', () => {
    let captured: number | undefined;
    service.getStatistics().subscribe({
      next: () => fail('expected an error, not a success'),
      error: err => (captured = err.status)
    });

    httpMock.expectOne(URL).flush('error', { status: 503, statusText: 'Service Unavailable' });

    expect(captured).toBe(503);
  });
});
