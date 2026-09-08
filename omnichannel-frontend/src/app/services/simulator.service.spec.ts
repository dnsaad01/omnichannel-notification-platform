import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SimulatorService } from './simulator.service';

/**
 * Every method on SimulatorService pipes through a catchError that
 * swallows any backend failure and resolves to a locally-fabricated
 * "SUCCESS" fallback instead (see each method's own comment: the Event
 * Simulator page must stay usable/demoable even with no backend running).
 * That means each method genuinely has two behaviors worth covering: the
 * normal pass-through of a real backend response, and the fallback taking
 * over when the request fails outright.
 */
describe('SimulatorService', () => {
  let service: SimulatorService;
  let httpMock: HttpTestingController;

  const BASE_URL = 'http://localhost:8082/api/v1/simulator';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(SimulatorService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('sendSingleEvent', () => {
    it('should POST to /send and pass through a real backend response', () => {
      let actual: any;
      service.sendSingleEvent({ eventId: 'evt-1' }).subscribe(response => (actual = response));

      const req = httpMock.expectOne(`${BASE_URL}/send`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ eventId: 'evt-1' });
      req.flush({ status: 'SUCCESS', eventId: 'evt-1' });

      expect(actual).toEqual({ status: 'SUCCESS', eventId: 'evt-1' });
    });

    it('should POST an empty body when no custom event is given', () => {
      service.sendSingleEvent().subscribe();
      const req = httpMock.expectOne(`${BASE_URL}/send`);
      expect(req.request.body).toEqual({});
      req.flush({});
    });

    it('should fall back to a locally-simulated SUCCESS response when the backend is unreachable', () => {
      let actual: any;
      service.sendSingleEvent({ eventId: 'evt-9' }).subscribe(response => (actual = response));

      httpMock.expectOne(`${BASE_URL}/send`).error(new ProgressEvent('error'));

      expect(actual.status).toBe('SUCCESS');
      expect(actual.event).toEqual({ eventId: 'evt-9' });
    });
  });

  describe('sendBatchEvents', () => {
    it('should POST to /batch?count= and pass through a real backend response', () => {
      let actual: any;
      service.sendBatchEvents(15).subscribe(response => (actual = response));

      const req = httpMock.expectOne(`${BASE_URL}/batch?count=15`);
      expect(req.request.method).toBe('POST');
      req.flush({ status: 'SUCCESS', count: 15 });

      expect(actual).toEqual({ status: 'SUCCESS', count: 15 });
    });

    it('should default count to 10 and fall back locally on failure, still reporting count 10', () => {
      let actual: any;
      service.sendBatchEvents().subscribe(response => (actual = response));

      const req = httpMock.expectOne(`${BASE_URL}/batch?count=10`);
      req.error(new ProgressEvent('error'));

      expect(actual.status).toBe('SUCCESS');
      expect(actual.count).toBe(10);
    });
  });

  describe('startSimulation', () => {
    it('should POST to /start?ratePerSec= and pass through a real backend response', () => {
      let actual: any;
      service.startSimulation(5).subscribe(response => (actual = response));

      const req = httpMock.expectOne(`${BASE_URL}/start?ratePerSec=5`);
      expect(req.request.method).toBe('POST');
      req.flush({ status: 'SUCCESS' });

      expect(actual).toEqual({ status: 'SUCCESS' });
    });

    it('should default ratePerSec to 2 and fall back locally on failure', () => {
      let actual: any;
      service.startSimulation().subscribe(response => (actual = response));

      const req = httpMock.expectOne(`${BASE_URL}/start?ratePerSec=2`);
      req.error(new ProgressEvent('error'));

      expect(actual.status).toBe('SUCCESS');
      expect(actual.details.ratePerSecond).toBe(2);
      expect(actual.details.active).toBeTrue();
    });
  });

  describe('stopSimulation', () => {
    it('should POST to /stop and pass through a real backend response', () => {
      let actual: any;
      service.stopSimulation().subscribe(response => (actual = response));

      const req = httpMock.expectOne(`${BASE_URL}/stop`);
      expect(req.request.method).toBe('POST');
      req.flush({ status: 'SUCCESS' });

      expect(actual).toEqual({ status: 'SUCCESS' });
    });

    it('should fall back locally with active:false on failure', () => {
      let actual: any;
      service.stopSimulation().subscribe(response => (actual = response));

      httpMock.expectOne(`${BASE_URL}/stop`).error(new ProgressEvent('error'));

      expect(actual.status).toBe('SUCCESS');
      expect(actual.details.active).toBeFalse();
    });
  });

  describe('getStatus', () => {
    it('should GET /status and pass through a real backend response', () => {
      let actual: any;
      service.getStatus().subscribe(response => (actual = response));

      const req = httpMock.expectOne(`${BASE_URL}/status`);
      expect(req.request.method).toBe('GET');
      const status = { active: true, ratePerSecond: 3, totalSent: 99, totalErrors: 1, targetTopic: 'notification.ingestion', timestamp: '2026-01-01T00:00:00' };
      req.flush(status);

      expect(actual).toEqual(status);
    });

    it('should fall back to a locally-simulated status when the backend is unreachable', () => {
      let actual: any;
      service.getStatus().subscribe(response => (actual = response));

      httpMock.expectOne(`${BASE_URL}/status`).error(new ProgressEvent('error'));

      expect(actual.active).toBeFalse();
      expect(actual.targetTopic).toBe('notification.ingestion');
    });
  });
});
