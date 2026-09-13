import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

// ngx-vflow API contract notes (verified against the library's source,
// projects/ngx-vflow-lib/src/lib/vflow/{interfaces,directives,
// components/node}):
//
//   1. Node.point (and .data) must be real Angular WritableSignals, not
//      plain objects. NodeModel's constructor does
//      `if (rawNode.point) this.point = rawNode.point;` — handing it a
//      plain {x,y} silently replaces the signal with a non-callable object,
//      so every internal `this.point()` call throws and that node's
//      rendering aborts. Nodes are built here by wrapping
//      point/data/width/height in Angular's own `signal()` — the same
//      pattern ngx-vflow's own drag-and-drop-nodes-demo component uses
//      (rather than the package's `createNode`/`createNodes` convenience
//      helpers), since plain `signal()` only depends on core Angular and
//      carries no ngx-vflow-version risk.
//   2. The custom-template directive selector is `nodeHtml`
//      (directives/template.directive.ts:
//      `@Directive({ selector: 'ng-template[nodeHtml]' })`). Its template
//      context is `{ $implicit: { node, data, selected, ... } }`, so the
//      binding is bare `let-ctx` (captures $implicit), then `ctx.node`,
//      `ctx.data()`, `ctx.selected()`.
//   3. html-template nodes default to a 100×50 SVG foreignObject
//      (NODE_DEFAULTS in node.interface.ts) — smaller than this card's
//      real content, which an SVG foreignObject clips rather than
//      overflows. An explicit width/height is passed per node instead.
//
// Selection/drag/connect event names are the ones VflowComponent actually
// exposes (components/vflow/vflow.component.ts's hostDirectives and
// directives/{selectable,changes-controller,node-drag-controller,
// connection-controller}.directive.ts) — see the handlers below.
import {
  Vflow,
  Node as VNode,
  Edge as VEdge,
  Connection,
  HtmlTemplateNode,
  NodeSelectedChange,
  NodeDragEndEvent
} from 'ngx-vflow';

import { DraftEdge, DraftNode, WorkflowNodeType } from '../../../models/workflow-draft.model';

export interface NodeMovedEvent {
  id: string;
  position: { x: number; y: number };
}

export interface ConnectionCreatedEvent {
  source: string;
  sourceHandle: 'yes' | 'no' | null;
  target: string;
}

export interface NodeDroppedEvent {
  type: WorkflowNodeType;
  position: { x: number; y: number };
}

const NODE_COLORS: Record<WorkflowNodeType, string> = {
  TRIGGER: '#f97316', // orange-500
  NOTIFICATION: '#38bdf8', // sky-400
  WAIT: '#fbbf24', // amber-400
  GATEWAY: '#a78bfa', // violet-400
  END: '#34d399' // emerald-400
};

/** Fixed card box handed to ngx-vflow per node (see item 3 above). Sized
 *  for the card markup in the template (icon-free two-line label). */
const NODE_WIDTH = 200;
const NODE_HEIGHT = 74;

/**
 * Wraps ngx-vflow (WorkflowCanvasComponent).
 * Public contract is entirely in terms of this app's own DraftNode/DraftEdge
 * model (see workflow-draft.model.ts) — WorkflowBuilderComponent never
 * touches ngx-vflow's own Node/Edge types, only this component does.
 */
@Component({
  selector: 'app-workflow-canvas',
  standalone: true,
  imports: [CommonModule, Vflow],
  templateUrl: './workflow-canvas.component.html'
})
export class WorkflowCanvasComponent implements OnChanges {
  @Input() nodes: DraftNode[] = [];
  @Input() edges: DraftEdge[] = [];
  @Input() selectedNodeId: string | null = null;

  @Output() nodeSelected = new EventEmitter<string | null>();
  @Output() nodeMoved = new EventEmitter<NodeMovedEvent>();
  @Output() connectionCreated = new EventEmitter<ConnectionCreatedEvent>();
  @Output() nodeDropped = new EventEmitter<NodeDroppedEvent>();

  @ViewChild('canvasHost', { static: true }) canvasHost!: ElementRef<HTMLDivElement>;

  vNodes: VNode[] = [];
  vEdges: VEdge[] = [];

  private readonly nodeColors = NODE_COLORS;

  ngOnChanges(changes: SimpleChanges) {
    if (changes['nodes']) {
      this.vNodes = this.nodes.map(n => this.toVNode(n));
    }
    if (changes['edges'] || changes['nodes']) {
      this.vEdges = this.edges.map(e => this.toVEdge(e));
    }
  }

  /** Typed lookup used from the template — keeps Record indexing out of the
   *  template expression itself. */
  getNodeColor(type: string): string {
    return this.nodeColors[type as WorkflowNodeType] ?? '#6b7280';
  }

  /** Every field ngx-vflow tracks reactively must be a real WritableSignal
   *  — see the file header. This mirrors HtmlTemplateNode<DraftNode> field
   *  for field (id.ts's `Node` union, SharedNode + HtmlTemplateNode). */
  private toVNode(node: DraftNode): VNode {
    const vNode: HtmlTemplateNode<DraftNode> = {
      id: node.id,
      point: signal({ x: node.position.x, y: node.position.y }),
      type: 'html-template',
      width: signal(NODE_WIDTH),
      height: signal(NODE_HEIGHT),
      data: signal(node)
    };
    return vNode;
  }

  private toVEdge(edge: DraftEdge): VEdge {
    const labelText = edge.sourceHandle === 'yes' ? 'oui' : edge.sourceHandle === 'no' ? 'non' : null;
    return {
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle ?? undefined,
      edgeLabels: labelText ? signal({ center: { type: 'default', text: labelText } }) : undefined
    } as VEdge;
  }

  /**
   * Real selection mechanism: there is no `nodeClick`/`paneClick` output on
   * <vflow> (that was invented in the earlier version of this file).
   * Selection is driven by a `[selectable]` directive placed on an element
   * inside our own node template (see the .html), which calls
   * SelectionService#select on click; every resulting change — including
   * background clicks, which the library's DefaultSelectionStrategy
   * auto-deselects on its own (default-selection.strategy.ts) — comes
   * through this one (nodesChanges.select) stream as an array of
   * {id, selected} changes.
   */
  onNodesSelectChange(changes: NodeSelectedChange[]) {
    const justSelected = changes.find(c => c.selected);
    if (justSelected) {
      this.nodeSelected.emit(justSelected.id);
      return;
    }
    const deselectedCurrent = changes.find(c => !c.selected && c.id === this.selectedNodeId);
    if (deselectedCurrent) {
      this.nodeSelected.emit(null);
    }
  }

  /** Real output is `nodeDragEnd` (NodeDragControllerDirective), not the
   *  invented `nodeDragStop`. Payload is { node }, where node.point is a
   *  real signal — no `$any()`/optional-chaining guesswork needed now that
   *  the shape is confirmed. */
  onNodeDragEnd(event: NodeDragEndEvent) {
    const point = event.node.point();
    this.nodeMoved.emit({ id: event.node.id, position: { x: point.x, y: point.y } });
  }

  /** `connect` is real (ConnectionControllerDirective, matched via the
   *  `[connect]` attribute-selector activated by this very event binding)
   *  and was already correctly wired — unchanged. */
  onConnect(connection: Connection) {
    const sourceHandle = (connection.sourceHandle as 'yes' | 'no' | '' | undefined) || null;
    this.connectionCreated.emit({
      source: connection.source,
      sourceHandle,
      target: connection.target
    });
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'copy';
    }
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    const type = event.dataTransfer?.getData('application/x-workflow-node-type') as WorkflowNodeType | '';
    if (!type) {
      return;
    }

    // Approximate: converts the browser drop point into canvas-local pixel
    // coordinates using the host element's bounding box, ignoring vflow's
    // own pan/zoom transform. Good enough at the default zoom the builder
    // opens with; a future improvement is VflowComponent's own
    // `documentPointToFlowPoint` (confirmed to exist and do exactly this —
    // see the real drag-and-drop-nodes-demo component in ngx-vflow's repo),
    // reachable via a `viewChild(VflowComponent)` here if drop placement
    // ever looks off at non-default zoom levels.
    const rect = this.canvasHost.nativeElement.getBoundingClientRect();
    const position = { x: event.clientX - rect.left, y: event.clientY - rect.top };
    this.nodeDropped.emit({ type, position });
  }
}
