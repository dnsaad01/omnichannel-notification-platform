import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { WorkflowBuilderComponent } from './workflow-builder.component';
import { WorkflowService } from '../../services/workflow.service';
import { TemplateService } from '../../services/template.service';
import { WorkflowResponse } from '../../models/workflow.model';
import { DraftNode } from '../../models/workflow-draft.model';

/**
 * Embeds WorkflowCanvasComponent, so this suite depends on the same
 * sandbox-only ngx-vflow stub (src/app/testing/ngx-vflow.stub.ts) that
 * WorkflowCanvasComponent's own spec documents — see that file's header.
 */
describe('WorkflowBuilderComponent', () => {
  let fixture: ComponentFixture<WorkflowBuilderComponent>;
  let component: WorkflowBuilderComponent;
  let workflowServiceSpy: jasmine.SpyObj<WorkflowService>;
  let templateServiceSpy: jasmine.SpyObj<TemplateService>;
  let router: Router;

  const savedWorkflow: WorkflowResponse = {
    id: 1,
    name: 'Cart Abandoned',
    description: '',
    status: 'DRAFT',
    triggerEventType: 'CART_ABANDONED',
    version: 1,
    parentWorkflowId: null,
    // Note the TRIGGER node's own config.eventType is populated too (unlike
    // a brand new starterGraph(), whose TRIGGER starts blank) — this is a
    // workflow that was already validly saved/activatable, so onSave's own
    // "trigger must be configured" check must pass against it as-loaded.
    definitionJson: JSON.stringify({
      nodes: [{ id: 'trigger-1', type: 'TRIGGER', name: 'Déclencheur', config: { eventType: 'CART_ABANDONED' }, position: { x: 60, y: 200 } }],
      edges: []
    }),
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00'
  };

  function configure(idParam: string | null) {
    workflowServiceSpy = jasmine.createSpyObj<WorkflowService>('WorkflowService', [
      'getWorkflowById', 'createWorkflow', 'updateWorkflow', 'activateWorkflow', 'deactivateWorkflow'
    ]);
    templateServiceSpy = jasmine.createSpyObj<TemplateService>('TemplateService', ['getAllTemplates']);
    templateServiceSpy.getAllTemplates.and.returnValue(of([{ id: 1, name: 'Welcome', channel: 'EMAIL', body: 'x', status: 'ACTIVE' }]));
    workflowServiceSpy.getWorkflowById.and.returnValue(of(savedWorkflow));

    TestBed.configureTestingModule({
      imports: [WorkflowBuilderComponent],
      providers: [
        provideRouter([]),
        { provide: WorkflowService, useValue: workflowServiceSpy },
        { provide: TemplateService, useValue: templateServiceSpy },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(idParam ? { id: idParam } : {}) } } }
      ]
    });

    fixture = TestBed.createComponent(WorkflowBuilderComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  }

  describe('create mode (/workflows/new)', () => {
    beforeEach(() => configure(null));

    it('should start from a fresh starterGraph and not attempt to load any workflow', () => {
      fixture.detectChanges();

      expect(component.isEditMode).toBeFalse();
      expect(component.workflowId).toBeNull();
      expect(component.graph.nodes.length).toBe(1);
      expect(component.graph.nodes[0].type).toBe('TRIGGER');
      expect(workflowServiceSpy.getWorkflowById).not.toHaveBeenCalled();
    });

    it('should map fetched templates to {id,name,channel} option objects', () => {
      fixture.detectChanges();
      expect(component.templates).toEqual([{ id: 1, name: 'Welcome', channel: 'EMAIL' }]);
    });

    it('should fall back to an empty template list when fetching templates fails', () => {
      templateServiceSpy.getAllTemplates.and.returnValue(throwError(() => new Error('down')));
      fixture.detectChanges();
      expect(component.templates).toEqual([]);
    });

    it('triggerEventType should read the TRIGGER node config, defaulting to empty string', () => {
      fixture.detectChanges();
      expect(component.triggerEventType).toBe('');

      component.graph.nodes[0].config['eventType'] = 'ORDER_CREATED';
      expect(component.triggerEventType).toBe('ORDER_CREATED');
    });

    it('selectedNode should resolve the node matching selectedNodeId, or null', () => {
      fixture.detectChanges();
      expect(component.selectedNode).toBeNull();

      component.selectedNodeId = component.graph.nodes[0].id;
      expect(component.selectedNode).toBe(component.graph.nodes[0]);
    });

    it('onNodeSelected should set selectedNodeId', () => {
      fixture.detectChanges();
      component.onNodeSelected('trigger-1');
      expect(component.selectedNodeId).toBe('trigger-1');
    });

    it('onNodeMoved should update the matching node position and ignore an unknown id', () => {
      fixture.detectChanges();
      const id = component.graph.nodes[0].id;

      component.onNodeMoved({ id, position: { x: 99, y: 55 } });
      expect(component.graph.nodes[0].position).toEqual({ x: 99, y: 55 });

      expect(() => component.onNodeMoved({ id: 'does-not-exist', position: { x: 0, y: 0 } })).not.toThrow();
    });

    it('onNodeDropped should reject a second TRIGGER node with a toast and not add it', () => {
      fixture.detectChanges();
      const before = component.graph.nodes.length;

      component.onNodeDropped({ type: 'TRIGGER', position: { x: 0, y: 0 } });

      expect(component.graph.nodes.length).toBe(before);
      expect(component.toastMessage).toContain('Un seul nœud Déclencheur');
    });

    it('onNodeDropped should add a new node with a default name/config and select it', () => {
      fixture.detectChanges();

      component.onNodeDropped({ type: 'WAIT', position: { x: 40, y: 60 } });

      const added = component.graph.nodes[component.graph.nodes.length - 1];
      expect(added.type).toBe('WAIT');
      expect(added.name).toBe('Attente');
      expect(added.config).toEqual({ duration: 1, unit: 'HOURS' });
      expect(added.position).toEqual({ x: 40, y: 60 });
      expect(component.selectedNodeId).toBe(added.id);
    });

    it('onConnectionCreated should reject a self-loop', () => {
      fixture.detectChanges();
      const id = component.graph.nodes[0].id;

      component.onConnectionCreated({ source: id, sourceHandle: null, target: id });

      expect(component.graph.edges).toEqual([]);
      expect(component.toastMessage).toContain('se relier à lui-même');
    });

    it('onConnectionCreated should silently ignore a connection from an unknown source node', () => {
      fixture.detectChanges();
      expect(() => component.onConnectionCreated({ source: 'ghost', sourceHandle: null, target: 'gateway-1' })).not.toThrow();
      expect(component.graph.edges).toEqual([]);
    });

    it('onConnectionCreated should reject an outgoing connection from an END node', () => {
      fixture.detectChanges();
      component.onNodeDropped({ type: 'END', position: { x: 0, y: 0 } });
      const endId = component.selectedNodeId!;

      component.onConnectionCreated({ source: endId, sourceHandle: null, target: 'somewhere' });

      expect(component.graph.edges).toEqual([]);
      expect(component.toastMessage).toContain('ne peut pas avoir de connexion sortante');
    });

    it('onConnectionCreated should replace any existing edge from a plain (non-GATEWAY) source', () => {
      fixture.detectChanges();
      const triggerId = component.graph.nodes[0].id;

      component.onConnectionCreated({ source: triggerId, sourceHandle: null, target: 'a' });
      component.onConnectionCreated({ source: triggerId, sourceHandle: null, target: 'b' });

      const fromTrigger = component.graph.edges.filter(e => e.source === triggerId);
      expect(fromTrigger.length).toBe(1);
      expect(fromTrigger[0].target).toBe('b');
    });

    it('onConnectionCreated should auto-assign the "yes" branch first, then "no", for a GATEWAY source with no explicit handle', () => {
      fixture.detectChanges();
      component.onNodeDropped({ type: 'GATEWAY', position: { x: 0, y: 0 } });
      const gatewayId = component.selectedNodeId!;

      component.onConnectionCreated({ source: gatewayId, sourceHandle: null, target: 'a' });
      expect(component.graph.edges.find(e => e.target === 'a')?.sourceHandle).toBe('yes');

      component.onConnectionCreated({ source: gatewayId, sourceHandle: null, target: 'b' });
      expect(component.graph.edges.find(e => e.target === 'b')?.sourceHandle).toBe('no');
      // Both branches now taken: the first ("yes") edge must still be there,
      // untouched, alongside the new "no" edge.
      expect(component.graph.edges.length).toBe(2);
    });

    it('onConnectionCreated should reject a third connection once both GATEWAY branches are taken', () => {
      fixture.detectChanges();
      component.onNodeDropped({ type: 'GATEWAY', position: { x: 0, y: 0 } });
      const gatewayId = component.selectedNodeId!;
      component.onConnectionCreated({ source: gatewayId, sourceHandle: null, target: 'a' });
      component.onConnectionCreated({ source: gatewayId, sourceHandle: null, target: 'b' });

      component.onConnectionCreated({ source: gatewayId, sourceHandle: null, target: 'c' });

      expect(component.graph.edges.length).toBe(2);
      expect(component.toastMessage).toContain('déjà ses deux branches');
    });

    it('onConnectionCreated should let an explicit yes/no handle replace only that branch', () => {
      fixture.detectChanges();
      component.onNodeDropped({ type: 'GATEWAY', position: { x: 0, y: 0 } });
      const gatewayId = component.selectedNodeId!;
      component.onConnectionCreated({ source: gatewayId, sourceHandle: 'yes', target: 'a' });
      component.onConnectionCreated({ source: gatewayId, sourceHandle: 'no', target: 'b' });

      component.onConnectionCreated({ source: gatewayId, sourceHandle: 'yes', target: 'c' });

      const branches = component.graph.edges.filter(e => e.source === gatewayId);
      expect(branches.length).toBe(2);
      expect(branches.find(e => e.sourceHandle === 'yes')?.target).toBe('c');
      expect(branches.find(e => e.sourceHandle === 'no')?.target).toBe('b');
    });

    it('onNodeNameChange should update only the selected node, and no-op with nothing selected', () => {
      fixture.detectChanges();
      const id = component.graph.nodes[0].id;

      component.onNodeNameChange('should be ignored');
      expect(component.graph.nodes[0].name).not.toBe('should be ignored');

      component.selectedNodeId = id;
      component.onNodeNameChange('Mon Déclencheur');
      expect(component.graph.nodes[0].name).toBe('Mon Déclencheur');
    });

    it('onNodeConfigChange should update only the selected node, and no-op with nothing selected', () => {
      fixture.detectChanges();
      const id = component.graph.nodes[0].id;

      component.onNodeConfigChange({ eventType: 'IGNORED' });
      expect(component.graph.nodes[0].config['eventType']).not.toBe('IGNORED');

      component.selectedNodeId = id;
      component.onNodeConfigChange({ eventType: 'CART_ABANDONED' });
      expect(component.graph.nodes[0].config).toEqual({ eventType: 'CART_ABANDONED' });
    });

    it('onDeleteNode should remove the node and any edges touching it, and clear the selection', () => {
      fixture.detectChanges();
      component.onNodeDropped({ type: 'END', position: { x: 0, y: 0 } });
      const endId = component.selectedNodeId!;
      const triggerId = component.graph.nodes[0].id;
      component.onConnectionCreated({ source: triggerId, sourceHandle: null, target: endId });
      component.selectedNodeId = endId;

      component.onDeleteNode();

      expect(component.graph.nodes.find((n: DraftNode) => n.id === endId)).toBeUndefined();
      expect(component.graph.edges.find(e => e.target === endId)).toBeUndefined();
      expect(component.selectedNodeId).toBeNull();
    });

    it('onDeleteNode should no-op when nothing is selected', () => {
      fixture.detectChanges();
      const before = component.graph.nodes.length;
      component.onDeleteNode();
      expect(component.graph.nodes.length).toBe(before);
    });

    it('onSave should reject a blank name without calling the service', () => {
      fixture.detectChanges();
      component.name = '   ';

      component.onSave();

      expect(workflowServiceSpy.createWorkflow).not.toHaveBeenCalled();
      expect(component.toastMessage).toContain('doit avoir un nom');
    });

    it('onSave should reject a workflow whose TRIGGER node has no eventType configured', () => {
      fixture.detectChanges();
      component.name = 'My Workflow';

      component.onSave();

      expect(workflowServiceSpy.createWorkflow).not.toHaveBeenCalled();
      expect(component.toastMessage).toContain("type d'événement");
    });

    it('onSave should POST via createWorkflow when not in edit mode, then navigate to the edit route', () => {
      fixture.detectChanges();
      component.name = 'My Workflow';
      component.graph.nodes[0].config['eventType'] = 'CART_ABANDONED';
      workflowServiceSpy.createWorkflow.and.returnValue(of(savedWorkflow));

      component.onSave();

      expect(workflowServiceSpy.createWorkflow).toHaveBeenCalledWith(jasmine.objectContaining({
        name: 'My Workflow',
        triggerEventType: 'CART_ABANDONED'
      }));
      expect(component.isSaving).toBeFalse();
      expect(component.toastMessage).toBe('💾 Workflow enregistré.');
      expect(router.navigate).toHaveBeenCalledWith(['/workflows', savedWorkflow.id, 'edit']);
    });

    it('onSave should surface the backend error message, falling back to a generic one', () => {
      fixture.detectChanges();
      component.name = 'My Workflow';
      component.graph.nodes[0].config['eventType'] = 'CART_ABANDONED';
      workflowServiceSpy.createWorkflow.and.returnValue(throwError(() => ({ error: { message: 'name already taken' } })));

      component.onSave();

      expect(component.isSaving).toBeFalse();
      expect(component.errorMessage).toBe('name already taken');
    });
  });

  describe('edit mode (/workflows/:id/edit)', () => {
    beforeEach(() => configure('1'));

    it('should load the existing workflow and populate name/description/status/graph from it', () => {
      fixture.detectChanges();

      expect(workflowServiceSpy.getWorkflowById).toHaveBeenCalledWith(1);
      expect(component.isEditMode).toBeTrue();
      expect(component.name).toBe('Cart Abandoned');
      expect(component.status).toBe('DRAFT');
      expect(component.graph.nodes.length).toBe(1);
      expect(component.isLoading).toBeFalse();
    });

    it('should record an error message when loading the workflow fails', () => {
      workflowServiceSpy.getWorkflowById.and.returnValue(throwError(() => ({ error: { message: 'not found' } })));
      fixture.detectChanges();

      expect(component.errorMessage).toBe('not found');
      expect(component.isLoading).toBeFalse();
    });

    it('onSave should PUT via updateWorkflow, and NOT navigate when the response keeps the same workflow id', () => {
      fixture.detectChanges();
      workflowServiceSpy.updateWorkflow.and.returnValue(of(savedWorkflow));

      component.onSave();

      expect(workflowServiceSpy.updateWorkflow).toHaveBeenCalledWith(1, jasmine.any(Object));
      expect(component.toastMessage).toBe('💾 Workflow enregistré.');
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('onSave should detect a new draft revision (different response id) and navigate to it with a distinct toast', () => {
      fixture.detectChanges();
      const revision: WorkflowResponse = { ...savedWorkflow, id: 2, status: 'DRAFT', parentWorkflowId: 1 };
      workflowServiceSpy.updateWorkflow.and.returnValue(of(revision));

      component.onSave();

      expect(component.toastMessage).toContain('nouvelle révision brouillon');
      expect(router.navigate).toHaveBeenCalledWith(['/workflows', 2, 'edit']);
      expect(component.workflowId).toBe(2);
    });

    it('activate should call activateWorkflow, apply the response, and show a success toast', () => {
      fixture.detectChanges();
      workflowServiceSpy.activateWorkflow.and.returnValue(of({ ...savedWorkflow, status: 'ACTIVE' }));

      component.activate();

      expect(workflowServiceSpy.activateWorkflow).toHaveBeenCalledWith(1);
      expect(component.status).toBe('ACTIVE');
      expect(component.toastMessage).toBe('✅ Workflow activé.');
    });

    it('activate should fall back to the graph-validation hint message on failure', () => {
      fixture.detectChanges();
      workflowServiceSpy.activateWorkflow.and.returnValue(throwError(() => ({})));

      component.activate();

      expect(component.errorMessage).toContain('vérifiez le graphe');
    });

    it('deactivate should call deactivateWorkflow, apply the response, and show a success toast', () => {
      fixture.detectChanges();
      workflowServiceSpy.deactivateWorkflow.and.returnValue(of({ ...savedWorkflow, status: 'DISABLED' }));

      component.deactivate();

      expect(component.status).toBe('DISABLED');
      expect(component.toastMessage).toBe('⏸️ Workflow désactivé.');
    });

    it('deactivate should fall back to a generic error message on failure', () => {
      fixture.detectChanges();
      workflowServiceSpy.deactivateWorkflow.and.returnValue(throwError(() => ({})));

      component.deactivate();

      expect(component.errorMessage).toContain('désactivation');
    });
  });

  describe('activate/deactivate with no workflowId (defensive no-op)', () => {
    beforeEach(() => configure(null));

    it('activate should do nothing when there is no workflowId', () => {
      fixture.detectChanges();
      component.activate();
      expect(workflowServiceSpy.activateWorkflow).not.toHaveBeenCalled();
    });

    it('deactivate should do nothing when there is no workflowId', () => {
      fixture.detectChanges();
      component.deactivate();
      expect(workflowServiceSpy.deactivateWorkflow).not.toHaveBeenCalled();
    });

    it('statusBadgeClass should return a distinct class per status, including null', () => {
      fixture.detectChanges();
      expect(component.statusBadgeClass('ACTIVE')).toContain('emerald');
      expect(component.statusBadgeClass('DRAFT')).toContain('amber');
      expect(component.statusBadgeClass('DISABLED')).toContain('gray-400');
      expect(component.statusBadgeClass('ARCHIVED')).toContain('gray-500');
      expect(component.statusBadgeClass(null)).toContain('gray-400');
    });
  });
});
