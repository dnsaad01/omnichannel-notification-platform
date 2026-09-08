import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WorkflowCanvasComponent } from './workflow-canvas.component';
import { DraftEdge, DraftNode } from '../../../models/workflow-draft.model';

/**
 * ngx-vflow (the real library workflow-canvas.component.ts wraps) can't be
 * installed in this sandbox — see src/app/testing/ngx-vflow.stub.ts and the
 * "ngx-vflow" path mapping in tsconfig.spec.json for why a local stub
 * stands in for it here (test-build only; neither ships). This suite
 * targets WorkflowCanvasComponent's own public class API — the part with
 * real logic — the same way ngx-vflow's real events would call it, rather
 * than relying on the stub to actually render draggable nodes.
 */
describe('WorkflowCanvasComponent', () => {
  let fixture: ComponentFixture<WorkflowCanvasComponent>;
  let component: WorkflowCanvasComponent;

  const triggerNode: DraftNode = { id: 'trigger-1', type: 'TRIGGER', name: 'Déclencheur', config: {}, position: { x: 10, y: 20 } };
  const gatewayNode: DraftNode = { id: 'gateway-1', type: 'GATEWAY', name: 'Condition', config: {}, position: { x: 100, y: 200 } };
  const anEdge: DraftEdge = { id: 'edge-1', source: 'trigger-1', sourceHandle: null, target: 'gateway-1' };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [WorkflowCanvasComponent] });
    fixture = TestBed.createComponent(WorkflowCanvasComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('ngOnChanges should convert DraftNodes into vflow html-template nodes carrying real signals', () => {
    component.nodes = [triggerNode];
    component.edges = [];
    component.ngOnChanges({ nodes: { currentValue: component.nodes, previousValue: [], firstChange: true, isFirstChange: () => true } as any });

    expect(component.vNodes.length).toBe(1);
    const vNode: any = component.vNodes[0];
    expect(vNode.id).toBe('trigger-1');
    expect(vNode.type).toBe('html-template');
    // point/width/height/data must be real WritableSignals (callable), not
    // plain objects — see the file's own header comment on why a plain
    // object silently breaks ngx-vflow's internal this.point() calls.
    expect(typeof vNode.point).toBe('function');
    expect(vNode.point()).toEqual({ x: 10, y: 20 });
    expect(typeof vNode.width).toBe('function');
    expect(vNode.width()).toBe(200);
    expect(typeof vNode.height).toBe('function');
    expect(vNode.height()).toBe(74);
    expect(typeof vNode.data).toBe('function');
    expect(vNode.data()).toEqual(triggerNode);
  });

  it('ngOnChanges should also rebuild edges whenever nodes change, even if edges itself did not', () => {
    component.nodes = [triggerNode, gatewayNode];
    component.edges = [anEdge];
    component.ngOnChanges({ nodes: { currentValue: component.nodes, previousValue: [], firstChange: true, isFirstChange: () => true } as any });

    expect(component.vEdges.length).toBe(1);
    expect((component.vEdges[0] as any).id).toBe('edge-1');
  });

  it('should label a GATEWAY yes-branch edge "oui" and a no-branch edge "non", and leave a plain edge unlabeled', () => {
    const yesEdge: DraftEdge = { id: 'e-yes', source: 'gateway-1', sourceHandle: 'yes', target: 'a' };
    const noEdge: DraftEdge = { id: 'e-no', source: 'gateway-1', sourceHandle: 'no', target: 'b' };
    component.edges = [yesEdge, noEdge, anEdge];
    component.ngOnChanges({ edges: { currentValue: component.edges, previousValue: [], firstChange: true, isFirstChange: () => true } as any });

    const [vYes, vNo, vPlain]: any[] = component.vEdges;
    expect(vYes.edgeLabels()).toEqual({ center: { type: 'default', text: 'oui' } });
    expect(vNo.edgeLabels()).toEqual({ center: { type: 'default', text: 'non' } });
    expect(vPlain.edgeLabels).toBeUndefined();
  });

  it('getNodeColor should return the color mapped to a known type and a neutral gray for an unknown one', () => {
    expect(component.getNodeColor('TRIGGER')).toBe('#f97316');
    expect(component.getNodeColor('END')).toBe('#34d399');
    expect(component.getNodeColor('SOMETHING_ELSE')).toBe('#6b7280');
  });

  it('onNodesSelectChange should emit the id of whichever node just became selected', () => {
    let emitted: string | null | undefined;
    component.nodeSelected.subscribe(id => (emitted = id));

    component.onNodesSelectChange([{ id: 'trigger-1', selected: true }]);

    expect(emitted).toBe('trigger-1');
  });

  it('onNodesSelectChange should emit null when the currently-selected node is the one that got deselected', () => {
    component.selectedNodeId = 'trigger-1';
    let emitted: string | null | undefined = 'unset';
    component.nodeSelected.subscribe(id => (emitted = id));

    component.onNodesSelectChange([{ id: 'trigger-1', selected: false }]);

    expect(emitted).toBeNull();
  });

  it('onNodesSelectChange should emit nothing when a different, non-selected node is deselected (e.g. background click)', () => {
    component.selectedNodeId = 'trigger-1';
    const emissions: (string | null)[] = [];
    component.nodeSelected.subscribe(id => emissions.push(id));

    component.onNodesSelectChange([{ id: 'gateway-1', selected: false }]);

    expect(emissions).toEqual([]);
  });

  it('onNodeDragEnd should emit the node id and its resolved {x,y} position', () => {
    let emitted: any;
    component.nodeMoved.subscribe(e => (emitted = e));

    component.onNodeDragEnd({ node: { id: 'trigger-1', point: () => ({ x: 42, y: 84 }) } } as any);

    expect(emitted).toEqual({ id: 'trigger-1', position: { x: 42, y: 84 } });
  });

  it('onConnect should emit source/target/sourceHandle, normalizing an empty-string handle to null', () => {
    let emitted: any;
    component.connectionCreated.subscribe(e => (emitted = e));

    component.onConnect({ source: 'trigger-1', sourceHandle: '', target: 'gateway-1' } as any);

    expect(emitted).toEqual({ source: 'trigger-1', sourceHandle: null, target: 'gateway-1' });
  });

  it('onConnect should pass through a real yes/no sourceHandle from a GATEWAY node', () => {
    let emitted: any;
    component.connectionCreated.subscribe(e => (emitted = e));

    component.onConnect({ source: 'gateway-1', sourceHandle: 'yes', target: 'end-1' } as any);

    expect(emitted.sourceHandle).toBe('yes');
  });

  it('onDragOver should preventDefault and set the drop effect to copy', () => {
    // A real DataTransfer created outside an actual browser-initiated drag
    // gesture silently refuses to persist dropEffect/effectAllowed writes
    // (a browser security restriction) — a plain mock object avoids that
    // and lets this test actually observe the assignment onDragOver makes.
    const dataTransfer: any = { dropEffect: 'none' };
    const event = { preventDefault: () => {}, dataTransfer } as unknown as DragEvent;
    spyOn(event, 'preventDefault');

    component.onDragOver(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(dataTransfer.dropEffect).toBe('copy');
  });

  it('onDrop should preventDefault and emit nothing when the drag payload has no node type', () => {
    const dataTransfer: any = { getData: () => '' };
    const event = { preventDefault: () => {}, dataTransfer } as unknown as DragEvent;
    spyOn(event, 'preventDefault');
    let emitted = false;
    component.nodeDropped.subscribe(() => (emitted = true));

    component.onDrop(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(emitted).toBeFalse();
  });

  it('onDrop should emit the node type and a canvas-local position derived from the host bounding box', () => {
    const payload = new Map<string, string>([['application/x-workflow-node-type', 'WAIT']]);
    const dataTransfer: any = { getData: (key: string) => payload.get(key) ?? '' };
    const event = { preventDefault: () => {}, dataTransfer, clientX: 150, clientY: 250 } as unknown as DragEvent;
    spyOn(component.canvasHost.nativeElement, 'getBoundingClientRect').and.returnValue({ left: 50, top: 100, right: 0, bottom: 0, width: 0, height: 0, x: 0, y: 0, toJSON: () => ({}) });

    let emitted: any;
    component.nodeDropped.subscribe(e => (emitted = e));

    component.onDrop(event);

    expect(emitted).toEqual({ type: 'WAIT', position: { x: 100, y: 150 } });
  });
});
