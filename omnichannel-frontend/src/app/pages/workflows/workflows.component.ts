import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { WorkflowService } from '../../services/workflow.service';
import { WorkflowResponse } from '../../models/workflow.model';

/**
 * Workflows list — backed by GET /api/workflows (WorkflowController,
 * Phase 1). Now that Phase 3's Builder exists, this page also drives
 * create/edit navigation and the activate/deactivate/duplicate lifecycle
 * actions (the WorkflowService methods for these were already added in
 * Phase 2, ahead of there being a UI that could call them).
 */
@Component({
  selector: 'app-workflows',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './workflows.component.html'
})
export class WorkflowsComponent implements OnInit {
  private workflowService = inject(WorkflowService);
  private router = inject(Router);

  workflows: WorkflowResponse[] = [];
  isLoading = false;
  errorMessage: string | null = null;
  toastMessage: string | null = null;

  ngOnInit() {
    this.fetchWorkflows();
  }

  fetchWorkflows() {
    this.isLoading = true;
    this.errorMessage = null;

    this.workflowService.getAllWorkflows().subscribe({
      next: (data) => {
        this.workflows = data ?? [];
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = 'Impossible de charger les workflows depuis le backend (ingestion-service tourne-t-il sur le port 8082 ?).';
        console.error('Failed to load workflows', err);
      }
    });
  }

  createNew() {
    this.router.navigate(['/workflows/new']);
  }

  edit(workflow: WorkflowResponse) {
    this.router.navigate(['/workflows', workflow.id, 'edit']);
  }

  viewExecutions(workflow: WorkflowResponse) {
    this.router.navigate(['/workflows/executions'], { queryParams: { workflowId: workflow.id } });
  }

  activate(workflow: WorkflowResponse) {
    this.workflowService.activateWorkflow(workflow.id).subscribe({
      next: () => {
        this.showToast(`✅ Workflow "${workflow.name}" activé.`);
        this.fetchWorkflows();
      },
      error: (err) => {
        this.errorMessage = err?.error?.message || 'Échec de l\'activation.';
      }
    });
  }

  deactivate(workflow: WorkflowResponse) {
    this.workflowService.deactivateWorkflow(workflow.id).subscribe({
      next: () => {
        this.showToast(`⏸️ Workflow "${workflow.name}" désactivé.`);
        this.fetchWorkflows();
      },
      error: (err) => {
        this.errorMessage = err?.error?.message || 'Échec de la désactivation.';
      }
    });
  }

  duplicate(workflow: WorkflowResponse) {
    this.workflowService.duplicateWorkflow(workflow.id).subscribe({
      next: (copy) => {
        this.showToast(`📄 Copie "${copy.name}" créée.`);
        this.fetchWorkflows();
      },
      error: (err) => {
        this.errorMessage = err?.error?.message || 'Échec de la duplication.';
      }
    });
  }

  private showToast(msg: string) {
    this.toastMessage = msg;
    setTimeout(() => {
      if (this.toastMessage === msg) {
        this.toastMessage = null;
      }
    }, 4000);
  }

  statusBadgeClass(status: string): string {
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
}
