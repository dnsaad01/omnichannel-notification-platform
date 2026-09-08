import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { WorkflowExecutionsComponent } from './workflow-executions.component';
import { WorkflowExecutionService } from '../../services/workflow-execution.service';
import { WorkflowService } from '../../services/workflow.service';
import { Page, WorkflowExecutionResponse, WorkflowResponse } from '../../models/workflow.model';

/**
 * This component imports RouterLink for its "back to workflow" links, so
 * Router must be a real instance (provideRouter([])) rather than a bare
 * jasmine spy — RouterLink calls real Router internals (createUrlTree etc.)
 * while resolving its href during change detection, which a spy object
 * without those methods would throw on. navigate() itself is still spied so
 * no real navigation actually happens.
 */
describe('WorkflowExecutionsComponent', () => {
  let fixture: ComponentFixture<WorkflowExecutionsComponent>;
  let component: WorkflowExecutionsComponent;
  let executionServiceSpy: jasmine.SpyObj<WorkflowExecutionService>;
  let workflowServiceSpy: jasmine.SpyObj<WorkflowService>;
  let router: Router;
  let queryParamMapSubject: Subject<ParamMap>;

  const anExecution: WorkflowExecutionResponse = {
    id: 10,
    workflowId: 1,
    workflowVersion: 1,
    status: 'RUNNING',
    currentNodeId: null,
    contextJson: null,
    nextWakeAt: null,
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

  beforeEach(async () => {
    executionServiceSpy = jasmine.createSpyObj<WorkflowExecutionService>('WorkflowExecutionService', ['list']);
    workflowServiceSpy = jasmine.createSpyObj<WorkflowService>('WorkflowService', ['getAllWorkflows']);
    queryParamMapSubject = new Subject<ParamMap>();

    executionServiceSpy.list.and.returnValue(of(aPage));
    workflowServiceSpy.getAllWorkflows.and.returnValue(of([{ id: 1, name: 'Cart Abandoned' } as WorkflowResponse]));

    await TestBed.configureTestingModule({
      imports: [WorkflowExecutionsComponent],
      providers: [
        provideRouter([]),
        { provide: WorkflowExecutionService, useValue: executionServiceSpy },
        { provide: WorkflowService, useValue: workflowServiceSpy },
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParamMapSubject.asObservable() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowExecutionsComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  it('should read workflowId/status from the initial query params, reset to page 0, and fetch', () => {
    fixture.detectChanges();
    queryParamMapSubject.next(convertToParamMap({ workflowId: '5', status: 'FAILED' }));

    expect(component.workflowIdFilter).toBe(5);
    expect(component.statusFilter).toBe('FAILED');
    expect(component.page).toBe(0);
    expect(executionServiceSpy.list).toHaveBeenCalled();
  });

  it('should default to no workflow filter and status ALL when the query params are empty', () => {
    fixture.detectChanges();
    queryParamMapSubject.next(convertToParamMap({}));

    expect(component.workflowIdFilter).toBeNull();
    expect(component.statusFilter).toBe('ALL');
  });

  it('should build a map of workflow names by id from getAllWorkflows, non-fatal on error', () => {
    fixture.detectChanges();
    queryParamMapSubject.next(convertToParamMap({}));

    expect(component.workflowNamesById.get(1)).toBe('Cart Abandoned');
  });

  it('should not blow up ngOnInit when loading workflow names fails', () => {
    workflowServiceSpy.getAllWorkflows.and.returnValue(throwError(() => new Error('down')));
    expect(() => {
      fixture.detectChanges();
      queryParamMapSubject.next(convertToParamMap({}));
    }).not.toThrow();
  });

  it('fetchExecutions should populate executions/totalPages/totalElements from the response', () => {
    fixture.detectChanges();
    queryParamMapSubject.next(convertToParamMap({}));

    expect(component.executions).toEqual([anExecution]);
    expect(component.totalPages).toBe(1);
    expect(component.totalElements).toBe(1);
    expect(component.isLoading).toBeFalse();
  });

  it('fetchExecutions should record an error message on failure', () => {
    executionServiceSpy.list.and.returnValue(throwError(() => new Error('down')));
    fixture.detectChanges();
    queryParamMapSubject.next(convertToParamMap({}));

    expect(component.errorMessage).toContain('Impossible de charger');
    expect(component.isLoading).toBeFalse();
  });

  it('onStatusFilterChange should navigate with the status query param, or null for ALL', () => {
    fixture.detectChanges();
    queryParamMapSubject.next(convertToParamMap({}));

    component.onStatusFilterChange('RUNNING');
    expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { status: 'RUNNING' } }));

    component.onStatusFilterChange('ALL');
    expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { status: null } }));
  });

  it('clearWorkflowFilter should navigate with workflowId cleared to null', () => {
    fixture.detectChanges();
    queryParamMapSubject.next(convertToParamMap({}));

    component.clearWorkflowFilter();
    expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { workflowId: null } }));
  });

  it('goToPage should ignore out-of-range pages and fetch for valid ones', () => {
    fixture.detectChanges();
    queryParamMapSubject.next(convertToParamMap({}));
    component.totalPages = 3;
    executionServiceSpy.list.calls.reset();

    component.goToPage(-1);
    component.goToPage(3);
    expect(executionServiceSpy.list).not.toHaveBeenCalled();

    component.goToPage(1);
    expect(component.page).toBe(1);
    expect(executionServiceSpy.list).toHaveBeenCalledTimes(1);
  });

  it('workflowLabel should use the resolved name, falling back to WF-{id}', () => {
    fixture.detectChanges();
    queryParamMapSubject.next(convertToParamMap({}));

    expect(component.workflowLabel(anExecution)).toBe('Cart Abandoned');
    expect(component.workflowLabel({ ...anExecution, workflowId: 999 })).toBe('WF-999');
  });

  it('statusBadgeClass should return a distinct class per status', () => {
    fixture.detectChanges();
    queryParamMapSubject.next(convertToParamMap({}));

    expect(component.statusBadgeClass('COMPLETED')).toContain('emerald');
    expect(component.statusBadgeClass('RUNNING')).toContain('sky');
    expect(component.statusBadgeClass('ADVANCING')).toContain('sky');
    expect(component.statusBadgeClass('WAITING')).toContain('amber');
    expect(component.statusBadgeClass('FAILED')).toContain('rose');
    expect(component.statusBadgeClass('UNKNOWN')).toContain('gray');
  });
});
