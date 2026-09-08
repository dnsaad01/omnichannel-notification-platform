import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WorkflowService } from './workflow.service';
import { WorkflowRequest, WorkflowResponse } from '../models/workflow.model';

/**
 * Full CRUD + lifecycle coverage for WorkflowService, mirroring
 * MonitoringService's own spec conventions: HttpClientTesting intercepts
 * every call (no real backend needed), and httpMock.verify() in afterEach
 * fails the test loudly if a method ever issues a request nobody asserted
 * on — as useful for catching an accidental extra call as a missing one.
 */
describe('WorkflowService', () => {
  let service: WorkflowService;
  let httpMock: HttpTestingController;

  const BASE_URL = 'http://localhost:8082/api/workflows';

  const aWorkflow: WorkflowResponse = {
    id: 1,
    name: 'Cart Abandoned',
    description: 'Reminds a customer who left items in their cart',
    status: 'ACTIVE',
    triggerEventType: 'CART_ABANDONED',
    version: 1,
    parentWorkflowId: null,
    definitionJson: '{"nodes":[],"edges":[]}',
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(WorkflowService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET the workflow list from /api/workflows', () => {
    let actual: WorkflowResponse[] | undefined;
    service.getAllWorkflows().subscribe(response => (actual = response));

    const req = httpMock.expectOne(BASE_URL);
    expect(req.request.method).toBe('GET');
    req.flush([aWorkflow]);

    expect(actual).toEqual([aWorkflow]);
  });

  it('should GET a single workflow by id', () => {
    let actual: WorkflowResponse | undefined;
    service.getWorkflowById(1).subscribe(response => (actual = response));

    const req = httpMock.expectOne(`${BASE_URL}/1`);
    expect(req.request.method).toBe('GET');
    req.flush(aWorkflow);

    expect(actual).toEqual(aWorkflow);
  });

  it('should POST a new workflow with the exact request body given', () => {
    const request: WorkflowRequest = {
      name: 'New Workflow',
      triggerEventType: 'CART_ABANDONED',
      description: '',
      definitionJson: ''
    };

    let actual: WorkflowResponse | undefined;
    service.createWorkflow(request).subscribe(response => (actual = response));

    const req = httpMock.expectOne(BASE_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ ...aWorkflow, status: 'DRAFT' });

    expect(actual?.status).toBe('DRAFT');
  });

  it('should PUT an update to /api/workflows/{id}', () => {
    const request: WorkflowRequest = {
      name: 'Renamed',
      triggerEventType: 'CART_ABANDONED',
      description: '',
      definitionJson: ''
    };

    service.updateWorkflow(1, request).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ ...aWorkflow, name: 'Renamed' });
  });

  it('should DELETE a workflow by id', () => {
    let completed = false;
    service.deleteWorkflow(1).subscribe(() => (completed = true));

    const req = httpMock.expectOne(`${BASE_URL}/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(completed).toBeTrue();
  });

  it('should POST to /activate with an empty body', () => {
    service.activateWorkflow(1).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/1/activate`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ ...aWorkflow, status: 'ACTIVE' });
  });

  it('should POST to /deactivate with an empty body', () => {
    service.deactivateWorkflow(1).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/1/deactivate`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...aWorkflow, status: 'DISABLED' });
  });

  it('should POST to /duplicate and surface the duplicated DRAFT copy', () => {
    let actual: WorkflowResponse | undefined;
    service.duplicateWorkflow(1).subscribe(response => (actual = response));

    const req = httpMock.expectOne(`${BASE_URL}/1/duplicate`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...aWorkflow, id: 2, name: 'Cart Abandoned (copie)', status: 'DRAFT' });

    expect(actual?.id).toBe(2);
    expect(actual?.status).toBe('DRAFT');
  });

  it('should propagate a backend error (e.g. 400 activation validation failure) to the caller rather than swallow it', () => {
    let capturedStatus: number | undefined;
    service.activateWorkflow(1).subscribe({
      next: () => fail('expected an error, not a success'),
      error: err => (capturedStatus = err.status)
    });

    httpMock.expectOne(`${BASE_URL}/1/activate`).flush(
      { status: 400, error: 'Bad Request', message: 'Workflow validation failed: no TRIGGER node' },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(capturedStatus).toBe(400);
  });
});
