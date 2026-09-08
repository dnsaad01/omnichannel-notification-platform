import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { WorkflowsComponent } from './workflows.component';
import { WorkflowService } from '../../services/workflow.service';
import { WorkflowResponse } from '../../models/workflow.model';

describe('WorkflowsComponent', () => {
  let fixture: ComponentFixture<WorkflowsComponent>;
  let component: WorkflowsComponent;
  let workflowServiceSpy: jasmine.SpyObj<WorkflowService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const aWorkflow: WorkflowResponse = {
    id: 1,
    name: 'Cart Abandoned',
    description: null,
    status: 'DRAFT',
    triggerEventType: 'CART_ABANDONED',
    version: 1,
    parentWorkflowId: null,
    definitionJson: '{}',
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00'
  };

  beforeEach(async () => {
    workflowServiceSpy = jasmine.createSpyObj<WorkflowService>('WorkflowService', [
      'getAllWorkflows', 'activateWorkflow', 'deactivateWorkflow', 'duplicateWorkflow'
    ]);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
    workflowServiceSpy.getAllWorkflows.and.returnValue(of([aWorkflow]));

    await TestBed.configureTestingModule({
      imports: [WorkflowsComponent],
      providers: [
        { provide: WorkflowService, useValue: workflowServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should fetch the workflow list on init', () => {
    expect(component.workflows).toEqual([aWorkflow]);
    expect(component.isLoading).toBeFalse();
  });

  it('should default workflows to an empty array when the backend returns null', () => {
    workflowServiceSpy.getAllWorkflows.and.returnValue(of(null as any));
    component.fetchWorkflows();
    expect(component.workflows).toEqual([]);
  });

  it('should record an error message when the fetch fails', () => {
    workflowServiceSpy.getAllWorkflows.and.returnValue(throwError(() => new Error('down')));
    component.fetchWorkflows();
    expect(component.errorMessage).toContain('Impossible de charger');
    expect(component.isLoading).toBeFalse();
  });

  it('createNew should navigate to /workflows/new', () => {
    component.createNew();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/workflows/new']);
  });

  it('edit should navigate to /workflows/{id}/edit', () => {
    component.edit(aWorkflow);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/workflows', 1, 'edit']);
  });

  it('viewExecutions should navigate to the executions list filtered by workflowId', () => {
    component.viewExecutions(aWorkflow);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/workflows/executions'], { queryParams: { workflowId: 1 } });
  });

  it('activate should show a success toast and refresh the list', () => {
    workflowServiceSpy.activateWorkflow.and.returnValue(of({ ...aWorkflow, status: 'ACTIVE' }));
    workflowServiceSpy.getAllWorkflows.calls.reset();

    component.activate(aWorkflow);

    expect(workflowServiceSpy.activateWorkflow).toHaveBeenCalledWith(1);
    expect(component.toastMessage).toContain('activé');
    expect(workflowServiceSpy.getAllWorkflows).toHaveBeenCalledTimes(1);
  });

  it('activate should surface a backend error message, falling back to a generic one when absent', () => {
    workflowServiceSpy.activateWorkflow.and.returnValue(throwError(() => ({ error: { message: 'no TRIGGER node' } })));
    component.activate(aWorkflow);
    expect(component.errorMessage).toBe('no TRIGGER node');

    workflowServiceSpy.activateWorkflow.and.returnValue(throwError(() => ({})));
    component.activate(aWorkflow);
    expect(component.errorMessage).toContain("l'activation");
  });

  it('deactivate should show a success toast and refresh the list', () => {
    workflowServiceSpy.deactivateWorkflow.and.returnValue(of({ ...aWorkflow, status: 'DISABLED' }));
    component.deactivate(aWorkflow);
    expect(component.toastMessage).toContain('désactivé');
  });

  it('deactivate should fall back to a generic error message on failure', () => {
    workflowServiceSpy.deactivateWorkflow.and.returnValue(throwError(() => ({})));
    component.deactivate(aWorkflow);
    expect(component.errorMessage).toContain('désactivation');
  });

  it('duplicate should show a toast naming the new copy and refresh the list', () => {
    workflowServiceSpy.duplicateWorkflow.and.returnValue(of({ ...aWorkflow, id: 2, name: 'Cart Abandoned (copie)', status: 'DRAFT' }));
    component.duplicate(aWorkflow);
    expect(component.toastMessage).toContain('Cart Abandoned (copie)');
  });

  it('duplicate should fall back to a generic error message on failure', () => {
    workflowServiceSpy.duplicateWorkflow.and.returnValue(throwError(() => ({})));
    component.duplicate(aWorkflow);
    expect(component.errorMessage).toContain('duplication');
  });

  it('statusBadgeClass should return a distinct class for each known status and a default for unknown ones', () => {
    expect(component.statusBadgeClass('ACTIVE')).toContain('emerald');
    expect(component.statusBadgeClass('DRAFT')).toContain('amber');
    expect(component.statusBadgeClass('DISABLED')).toContain('gray-400');
    expect(component.statusBadgeClass('ARCHIVED')).toContain('gray-500');
    expect(component.statusBadgeClass('UNKNOWN')).toContain('gray-400');
  });
});
