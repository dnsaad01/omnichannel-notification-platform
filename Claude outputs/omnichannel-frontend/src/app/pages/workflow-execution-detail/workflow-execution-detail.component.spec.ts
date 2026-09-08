import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { WorkflowExecutionDetailComponent } from './workflow-execution-detail.component';
import { WorkflowExecutionService } from '../../services/workflow-execution.service';
import { WorkflowService } from '../../services/workflow.service';
import { WorkflowExecutionResponse, WorkflowResponse } from '../../models/workflow.model';

describe('WorkflowExecutionDetailComponent', () => {
  let fixture: ComponentFixture<WorkflowExecutionDetailComponent>;
  let component: WorkflowExecutionDetailComponent;
  let executionServiceSpy: jasmine.SpyObj<WorkflowExecutionService>;
  let workflowServiceSpy: jasmine.SpyObj<WorkflowService>;

  const runningExecution: WorkflowExecutionResponse = {
    id: 10,
    workflowId: 1,
    workflowVersion: 1,
    status: 'RUNNING',
    currentNodeId: 'wait-1',
    contextJson: '{"cartId":"c1"}',
    nextWakeAt: null,
    startedAt: '2026-01-01T00:00:00',
    lastActivityAt: '2026-01-01T00:00:00',
    completedAt: null,
    logs: [{ id: 1, nodeId: 'wait-1', nodeType: 'WAIT', message: 'Waiting', level: 'INFO', createdAt: '2026-01-01T00:00:00' }]
  };

  function configure(id = '10') {
    executionServiceSpy = jasmine.createSpyObj<WorkflowExecutionService>('WorkflowExecutionService', ['getById']);
    workflowServiceSpy = jasmine.createSpyObj<WorkflowService>('WorkflowService', ['getWorkflowById']);
    executionServiceSpy.getById.and.returnValue(of(runningExecution));
    workflowServiceSpy.getWorkflowById.and.returnValue(of({ name: 'Cart Abandoned' } as WorkflowResponse));

    TestBed.configureTestingModule({
      imports: [WorkflowExecutionDetailComponent],
      providers: [
        provideRouter([]),
        { provide: WorkflowExecutionService, useValue: executionServiceSpy },
        { provide: WorkflowService, useValue: workflowServiceSpy },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id }) } } }
      ]
    });

    fixture = TestBed.createComponent(WorkflowExecutionDetailComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => {
    component?.ngOnDestroy();
  });

  it('should read the execution id from the route and fetch it on init', () => {
    configure('10');
    fixture.detectChanges();

    expect(executionServiceSpy.getById).toHaveBeenCalledWith(10);
    expect(component.execution).toEqual(runningExecution);
    expect(component.isLoading).toBeFalse();
  });

  it('should pretty-print a valid contextJson', () => {
    configure();
    fixture.detectChanges();
    expect(component.prettyContext).toBe(JSON.stringify({ cartId: 'c1' }, null, 2));
  });

  it('should leave prettyContext null when contextJson is null', () => {
    executionServiceSpy = jasmine.createSpyObj<WorkflowExecutionService>('WorkflowExecutionService', ['getById']);
    workflowServiceSpy = jasmine.createSpyObj<WorkflowService>('WorkflowService', ['getWorkflowById']);
    executionServiceSpy.getById.and.returnValue(of({ ...runningExecution, contextJson: null }));
    workflowServiceSpy.getWorkflowById.and.returnValue(of({ name: 'x' } as WorkflowResponse));

    TestBed.configureTestingModule({
      imports: [WorkflowExecutionDetailComponent],
      providers: [
        provideRouter([]),
        { provide: WorkflowExecutionService, useValue: executionServiceSpy },
        { provide: WorkflowService, useValue: workflowServiceSpy },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '10' }) } } }
      ]
    });
    fixture = TestBed.createComponent(WorkflowExecutionDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.prettyContext).toBeNull();
  });

  it('should fall back to the raw string when contextJson is not valid JSON', () => {
    executionServiceSpy = jasmine.createSpyObj<WorkflowExecutionService>('WorkflowExecutionService', ['getById']);
    workflowServiceSpy = jasmine.createSpyObj<WorkflowService>('WorkflowService', ['getWorkflowById']);
    executionServiceSpy.getById.and.returnValue(of({ ...runningExecution, contextJson: 'not json' }));
    workflowServiceSpy.getWorkflowById.and.returnValue(of({ name: 'x' } as WorkflowResponse));

    TestBed.configureTestingModule({
      imports: [WorkflowExecutionDetailComponent],
      providers: [
        provideRouter([]),
        { provide: WorkflowExecutionService, useValue: executionServiceSpy },
        { provide: WorkflowService, useValue: workflowServiceSpy },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '10' }) } } }
      ]
    });
    fixture = TestBed.createComponent(WorkflowExecutionDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.prettyContext).toBe('not json');
  });

  it('should fetch the workflow name once, and null it out gracefully on error', () => {
    configure();
    workflowServiceSpy.getWorkflowById.and.returnValue(throwError(() => new Error('down')));
    fixture.detectChanges();
    expect(component.workflowName).toBeNull();
  });

  it('should not re-fetch the workflow name once it has already been resolved', fakeAsync(() => {
    configure();
    fixture.detectChanges();
    expect(component.workflowName).toBe('Cart Abandoned');
    workflowServiceSpy.getWorkflowById.calls.reset();

    tick(5000);

    expect(workflowServiceSpy.getWorkflowById).not.toHaveBeenCalled();
  }));

  it('should record an error message when fetching the execution fails', () => {
    configure();
    executionServiceSpy.getById.and.returnValue(throwError(() => new Error('down')));
    fixture.detectChanges();

    expect(component.errorMessage).toContain('Impossible de charger');
    expect(component.isLoading).toBeFalse();
  });

  it('should start polling every 5s while the execution is in a live status', fakeAsync(() => {
    configure();
    fixture.detectChanges();
    executionServiceSpy.getById.calls.reset();

    tick(5000);
    expect(executionServiceSpy.getById).toHaveBeenCalledTimes(1);

    component.ngOnDestroy();
    tick(5000);
    expect(executionServiceSpy.getById).toHaveBeenCalledTimes(1);
  }));

  it('should stop polling once the execution reaches a terminal status', fakeAsync(() => {
    configure();
    executionServiceSpy.getById.and.returnValue(of({ ...runningExecution, status: 'COMPLETED' }));
    fixture.detectChanges();
    executionServiceSpy.getById.calls.reset();

    tick(10000);

    expect(executionServiceSpy.getById).not.toHaveBeenCalled();
  }));

  it('statusBadgeClass and logDotClass should return distinct classes for known values', () => {
    configure();
    fixture.detectChanges();

    expect(component.statusBadgeClass('COMPLETED')).toContain('emerald');
    expect(component.statusBadgeClass('FAILED')).toContain('rose');
    expect(component.statusBadgeClass('UNKNOWN')).toContain('gray');
    expect(component.logDotClass('ERROR')).toContain('rose');
    expect(component.logDotClass('INFO')).toContain('orange');
  });
});
