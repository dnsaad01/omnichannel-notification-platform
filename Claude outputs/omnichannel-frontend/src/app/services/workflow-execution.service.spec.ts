import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WorkflowExecutionService } from './workflow-execution.service';
import { Page, WorkflowExecutionResponse } from '../models/workflow.model';

describe('WorkflowExecutionService', () => {
  let service: WorkflowExecutionService;
  let httpMock: HttpTestingController;

  const BASE_URL = 'http://localhost:8082/api/workflow-executions';

  const anExecution: WorkflowExecutionResponse = {
    id: 10,
    workflowId: 1,
    workflowVersion: 1,
    status: 'RUNNING',
    currentNodeId: 'wait-1',
    contextJson: '{}',
    nextWakeAt: '2026-01-02T00:00:00',
    startedAt: '2026-01-01T00:00:00',
    lastActivityAt: '2026-01-01T00:00:00',
    completedAt: null,
    logs: null
  };

  const aPage: Page<WorkflowExecutionResponse> = {
    content: [anExecution],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 20,
    first: true,
    last: true
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(WorkflowExecutionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET the execution list with default page=0 and size=20 when no options are given', () => {
    let actual: Page<WorkflowExecutionResponse> | undefined;
    service.list().subscribe(r => (actual = r));

    const req = httpMock.expectOne(r => r.url === BASE_URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('workflowId')).toBeFalse();
    expect(req.request.params.has('status')).toBeFalse();
    req.flush(aPage);

    expect(actual).toEqual(aPage);
  });

  it('should include workflowId and status as query params when given', () => {
    service.list({ workflowId: 7, status: 'FAILED', page: 2, size: 10 }).subscribe();

    const req = httpMock.expectOne(r => r.url === BASE_URL);
    expect(req.request.params.get('workflowId')).toBe('7');
    expect(req.request.params.get('status')).toBe('FAILED');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(aPage);
  });

  it('should GET a single execution by id, including its logs', () => {
    const withLogs = { ...anExecution, logs: [{ id: 1, nodeId: 'wait-1', nodeType: 'WAIT', message: 'Waiting', level: 'INFO' as const, createdAt: '2026-01-01T00:00:00' }] };

    let actual: WorkflowExecutionResponse | undefined;
    service.getById(10).subscribe(r => (actual = r));

    const req = httpMock.expectOne(`${BASE_URL}/10`);
    expect(req.request.method).toBe('GET');
    req.flush(withLogs);

    expect(actual?.logs?.length).toBe(1);
  });

  it('should propagate a 404 when the execution does not exist', () => {
    let captured: number | undefined;
    service.getById(999).subscribe({
      next: () => fail('expected an error, not a success'),
      error: err => (captured = err.status)
    });

    httpMock.expectOne(`${BASE_URL}/999`).flush('not found', { status: 404, statusText: 'Not Found' });

    expect(captured).toBe(404);
  });
});
