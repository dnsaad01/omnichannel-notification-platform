import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BusinessEventRequest, BusinessEventResponse, BusinessEventService } from './business-event.service';

/**
 * BusinessEventService is the Event Simulator page's client for the
 * *business*-event layer (/api/business-events/publish), distinct from
 * SimulatorService's lower-level /api/v1/simulator endpoints — see this
 * service's own class doc comment. Its only job is a single POST, so the
 * suite is deliberately small but still asserts the exact URL and body,
 * since a business event only reaches WorkflowTriggerConsumer (and
 * therefore only spawns a WorkflowExecution) if both are correct.
 */
describe('BusinessEventService', () => {
  let service: BusinessEventService;
  let httpMock: HttpTestingController;

  const BASE_URL = 'http://localhost:8082/api/business-events';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(BusinessEventService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should POST the given event to /api/business-events/publish with the exact body given', () => {
    const request: BusinessEventRequest = {
      eventType: 'CART_ABANDONED',
      payload: { cartId: 'cart-42', customerId: 'cust-1' }
    };

    let actual: BusinessEventResponse | undefined;
    service.publish(request).subscribe(response => (actual = response));

    const req = httpMock.expectOne(`${BASE_URL}/publish`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);

    const response: BusinessEventResponse = { status: 'PUBLISHED', eventType: 'CART_ABANDONED', payload: request.payload };
    req.flush(response);

    expect(actual).toEqual(response);
  });

  it('should propagate a backend error to the caller rather than swallow it', () => {
    let capturedStatus: number | undefined;
    service.publish({ eventType: 'CART_ABANDONED', payload: {} }).subscribe({
      next: () => fail('expected an error, not a success'),
      error: err => (capturedStatus = err.status)
    });

    httpMock.expectOne(`${BASE_URL}/publish`).flush(
      { message: 'No ACTIVE workflow matches this trigger event type' },
      { status: 404, statusText: 'Not Found' }
    );

    expect(capturedStatus).toBe(404);
  });
});
