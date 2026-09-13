import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { WorkflowService } from '../../services/workflow.service';
import { TemplateService } from '../../services/template.service';
import { WorkflowRequest, WorkflowResponse, WorkflowStatus } from '../../models/workflow.model';
import {
  DraftEdge,
  DraftNode,
  WorkflowGraph,
  WorkflowNodeType,
  createEdgeId,
  createNodeId,
  defaultConfigFor,
  defaultNameFor,
  parseDefinitionJson,
  serializeDefinitionJson,
  starterGraph
} from '../../models/workflow-draft.model';
import { NodePaletteComponent } from './node-palette/node-palette.component';
import { WorkflowCanvasComponent, ConnectionCreatedEvent, NodeDroppedEvent, NodeMovedEvent } from './workflow-canvas/workflow-canvas.component';
import { NodeConfigPanelComponent } from './node-config-panel/node-config-panel.component';
import { TemplateOption } from './config-panels/notification-config.component';

/**
 * The Workflow Builder page (WorkflowBuilderComponent): toolbar
 * (name/description, Save/Activate/Deactivate) + 3-pane layout
 * (palette | canvas | config panel). Owns the single source of truth for
 * the graph being edited — a plain WorkflowGraph (workflow-draft.model.ts)
 * that serializes 1:1 onto the backend's definitionJson.
 *
 * Two modes, same component: /workflows/new (no :id — starts from
 * starterGraph()) and /workflows/:id/edit (loads the existing workflow and
 * parses its definitionJson). Both save through the same WorkflowService
 * methods.
 */
@Component({
  selector: 'app-workflow-builder',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, LucideAngularModule, NodePaletteComponent, WorkflowCanvasComponent, NodeConfigPanelComponent],
  templateUrl: './workflow-builder.component.html'
})
export class WorkflowBuilderComponent implements OnInit {
  private workflowService = inject(WorkflowService);
  private templateService = inject(TemplateService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  workflowId: number | null = null;
  name = '';
  description = '';
  status: WorkflowStatus | null = null;

  graph: WorkflowGraph = starterGraph();
  selectedNodeId: string | null = null;

  templates: TemplateOption[] = [];

  isLoading = false;
  isSaving = false;
  errorMessage: string | null = null;
  toastMessage: string | null = null;
  toastIcon: string = 'circle-check-big';

  get selectedNode(): DraftNode | null {
    return this.graph.nodes.find(n => n.id === this.selectedNodeId) ?? null;
  }

  get triggerEventType(): string {
    return this.graph.nodes.find(n => n.type === 'TRIGGER')?.config?.['eventType'] || '';
  }

  get isEditMode(): boolean {
    return this.workflowId !== null;
  }

  ngOnInit() {
    this.templateService.getAllTemplates().subscribe({
      next: (data: any[]) => {
        this.templates = (data ?? []).map(t => ({ id: t.id, name: t.name, channel: t.channel }));
      },
      error: () => {
        this.templates = [];
      }
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.workflowId = Number(idParam);
      this.loadWorkflow(this.workflowId);
    }
  }

  loadWorkflow(id: number) {
    this.isLoading = true;
    this.errorMessage = null;

    this.workflowService.getWorkflowById(id).subscribe({
      next: (wf) => this.applyWorkflowResponse(wf),
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = this.extractErrorMessage(err, 'Impossible de charger ce workflow.');
      }
    });
  }

  private applyWorkflowResponse(wf: WorkflowResponse) {
    this.workflowId = wf.id;
    this.name = wf.name;
    this.description = wf.description ?? '';
    this.status = wf.status;
    this.graph = parseDefinitionJson(wf.definitionJson);
    this.selectedNodeId = null;
    this.isLoading = false;
  }

  // ─── Canvas event handlers ────────────────────────────────────────────

  onNodeSelected(id: string | null) {
    this.selectedNodeId = id;
  }

  onNodeMoved(event: NodeMovedEvent) {
    const node = this.graph.nodes.find(n => n.id === event.id);
    if (node) {
      node.position = event.position;
    }
  }

  onNodeDropped(event: NodeDroppedEvent) {
    if (event.type === 'TRIGGER' && this.graph.nodes.some(n => n.type === 'TRIGGER')) {
      this.showToast('Un seul nœud Déclencheur est autorisé par workflow.', 'triangle-alert');
      return;
    }

    const newNode: DraftNode = {
      id: createNodeId(event.type),
      type: event.type,
      name: defaultNameFor(event.type),
      config: defaultConfigFor(event.type),
      position: event.position
    };

    this.graph = { ...this.graph, nodes: [...this.graph.nodes, newNode] };
    this.selectedNodeId = newNode.id;
  }

  onConnectionCreated(event: ConnectionCreatedEvent) {
    if (event.source === event.target) {
      this.showToast('Un nœud ne peut pas se relier à lui-même.', 'triangle-alert');
      return;
    }

    const sourceNode = this.graph.nodes.find(n => n.id === event.source);
    if (!sourceNode) {
      return;
    }

    if (sourceNode.type === 'END') {
      this.showToast('Un nœud Fin ne peut pas avoir de connexion sortante.', 'triangle-alert');
      return;
    }

    let edges = this.graph.edges.filter(e => e.source !== event.source);
    let sourceHandle: 'yes' | 'no' | null = null;

    if (sourceNode.type === 'GATEWAY') {
      const existingBranches = this.graph.edges.filter(e => e.source === event.source).map(e => e.sourceHandle);
      if (event.sourceHandle === 'yes' || event.sourceHandle === 'no') {
        sourceHandle = event.sourceHandle;
        edges = this.graph.edges.filter(e => !(e.source === event.source && e.sourceHandle === sourceHandle));
      } else if (!existingBranches.includes('yes')) {
        sourceHandle = 'yes';
        edges = this.graph.edges;
      } else if (!existingBranches.includes('no')) {
        sourceHandle = 'no';
        edges = this.graph.edges;
      } else {
        this.showToast('Ce nœud Gateway a déjà ses deux branches (oui/non).', 'triangle-alert');
        return;
      }
    }

    const newEdge: DraftEdge = {
      id: createEdgeId(),
      source: event.source,
      sourceHandle,
      target: event.target
    };

    this.graph = { ...this.graph, edges: [...edges, newEdge] };
  }

  onNodeNameChange(name: string) {
    const id = this.selectedNodeId;
    if (!id) {
      return;
    }
    this.graph = {
      ...this.graph,
      nodes: this.graph.nodes.map(n => (n.id === id ? { ...n, name } : n))
    };
  }

  onNodeConfigChange(config: Record<string, any>) {
    const id = this.selectedNodeId;
    if (!id) {
      return;
    }
    this.graph = {
      ...this.graph,
      nodes: this.graph.nodes.map(n => (n.id === id ? { ...n, config } : n))
    };
  }

  onDeleteNode() {
    const id = this.selectedNodeId;
    if (!id) {
      return;
    }
    this.graph = {
      nodes: this.graph.nodes.filter(n => n.id !== id),
      edges: this.graph.edges.filter(e => e.source !== id && e.target !== id)
    };
    this.selectedNodeId = null;
  }

  // ─── Save / Activate / Deactivate ─────────────────────────────────────

  /**
   * Bound to the toolbar's "Enregistrer" button. Gathers the workflow name/
   * description plus the current graph (this.graph.nodes / this.graph.edges,
   * mutated live by the canvas + config-panel event handlers above) into a
   * WorkflowRequest and POSTs or PUTs it via WorkflowService, depending on
   * whether we're creating a new workflow or editing an existing one.
   *
   * The graph's nodes/edges aren't sent as separate top-level fields: the
   * backend's WorkflowRequest DTO only has a single `definitionJson` string
   * field (see workflow.model.ts's WorkflowRequest doc comment), so
   * serializeDefinitionJson(this.graph) (workflow-draft.model.ts) is what
   * actually carries {nodes, edges} across the wire, JSON-stringified into
   * that one field.
   */
  onSave() {
    if (!this.name.trim()) {
      this.showToast('Le workflow doit avoir un nom.', 'triangle-alert');
      return;
    }
    if (!this.triggerEventType) {
      this.showToast('Configurez le type d\'événement sur le nœud Déclencheur avant d\'enregistrer.', 'triangle-alert');
      return;
    }

    this.isSaving = true;
    this.errorMessage = null;

    const request: WorkflowRequest = {
      name: this.name,
      description: this.description,
      triggerEventType: this.triggerEventType,
      definitionJson: serializeDefinitionJson(this.graph)
    };

    const save$ = this.isEditMode
      ? this.workflowService.updateWorkflow(this.workflowId!, request)
      : this.workflowService.createWorkflow(request);

    save$.subscribe({
      next: (wf) => {
        this.isSaving = false;
        const wasNewRevision = this.isEditMode && wf.id !== this.workflowId;
        this.applyWorkflowResponse(wf);

        if (wasNewRevision) {
          this.showToast('Ce workflow était ACTIF — une nouvelle révision brouillon a été créée.', 'save');
          this.router.navigate(['/workflows', wf.id, 'edit']);
        } else {
          this.showToast('Workflow enregistré.', 'save');
          if (!this.route.snapshot.paramMap.get('id')) {
            this.router.navigate(['/workflows', wf.id, 'edit']);
          }
        }
      },
      error: (err) => {
        this.isSaving = false;
        this.errorMessage = this.extractErrorMessage(err, 'Échec de l\'enregistrement du workflow.');
      }
    });
  }

  activate() {
    if (!this.workflowId) {
      return;
    }
    this.errorMessage = null;
    this.workflowService.activateWorkflow(this.workflowId).subscribe({
      next: (wf) => {
        this.applyWorkflowResponse(wf);
        this.showToast('Workflow activé.', 'circle-check-big');
      },
      error: (err) => {
        this.errorMessage = this.extractErrorMessage(err, 'Échec de l\'activation — vérifiez le graphe (un seul Déclencheur, chaque Gateway avec ses deux branches, aucun nœud isolé).');
      }
    });
  }

  deactivate() {
    if (!this.workflowId) {
      return;
    }
    this.workflowService.deactivateWorkflow(this.workflowId).subscribe({
      next: (wf) => {
        this.applyWorkflowResponse(wf);
        this.showToast('Workflow désactivé.', 'pause');
      },
      error: (err) => {
        this.errorMessage = this.extractErrorMessage(err, 'Échec de la désactivation.');
      }
    });
  }

  statusBadgeClass(status: string | null): string {
    switch (status) {
      case 'ACTIVE':
        return 'text-emerald-400 bg-emerald-950/50 border border-emerald-900';
      case 'DRAFT':
        return 'text-amber-400 bg-amber-950/50 border border-amber-900';
      case 'DISABLED':
        return 'text-gray-400 bg-gray-800/50 border border-gray-700';
      case 'ARCHIVED':
        return 'text-gray-500 bg-gray-900 border border-gray-800';
      default:
        return 'text-gray-400 bg-gray-800/50 border border-gray-700';
    }
  }

  private extractErrorMessage(err: any, fallback: string): string {
    return err?.error?.message || fallback;
  }

  private showToast(msg: string, icon: string = 'circle-check-big') {
    this.toastMessage = msg;
    this.toastIcon = icon;
    setTimeout(() => {
      if (this.toastMessage === msg) {
        this.toastMessage = null;
      }
    }, 4000);
  }
}
