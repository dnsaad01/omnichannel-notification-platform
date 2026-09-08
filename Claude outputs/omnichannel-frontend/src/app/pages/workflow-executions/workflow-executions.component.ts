import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { WorkflowExecutionService } from '../../services/workflow-execution.service';
import { WorkflowService } from '../../services/workflow.service';
import { ExecutionStatus, WorkflowExecutionResponse } from '../../models/workflow.model';

const STATUS_OPTIONS: (ExecutionStatus | 'ALL')[] = ['ALL', 'RUNNING', 'WAITING', 'ADVANCING', 'COMPLETED', 'FAILED'];

/**
 * Read-only Executions list — backed by GET /api/workflow-executions
 * (WorkflowExecutionController, Phase 1). Supports the two filters the
 * backend endpoint takes (workflowId, status) as URL query params, so
 * "Voir exécutions" links from the Workflows page land here pre-filtered
 * and the filter state survives a refresh/bookmark.
 */
@Component({
  selector: 'app-workflow-executions',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './workflow-executions.component.html'
})
export class WorkflowExecutionsComponent implements OnInit {
  private executionService = inject(WorkflowExecutionService);
  private workflowService = inject(WorkflowService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly statusOptions = STATUS_OPTIONS;

  executions: WorkflowExecutionResponse[] = [];
  workflowNamesById = new Map<number, string>();

  workflowIdFilter: number | null = null;
  statusFilter: ExecutionStatus | 'ALL' = 'ALL';

  page = 0;
  pageSize = 20;
  totalPages = 0;
  totalElements = 0;

  isLoading = false;
  errorMessage: string | null = null;

  ngOnInit() {
    this.route.queryParamMap.subscribe(params => {
      const workflowId = params.get('workflowId');
      const status = params.get('status');
      this.workflowIdFilter = workflowId ? Number(workflowId) : null;
      this.statusFilter = (status as ExecutionStatus) ?? 'ALL';
      this.page = 0;
      this.fetchExecutions();
    });

    this.workflowService.getAllWorkflows().subscribe({
      next: (workflows) => {
        this.workflowNamesById = new Map(workflows.map(w => [w.id, w.name]));
      },
      error: () => {
        // Non-fatal — the table falls back to "WF-{id}" if names can't be loaded.
      }
    });
  }

  fetchExecutions() {
    this.isLoading = true;
    this.errorMessage = null;

    this.executionService.list({
      workflowId: this.workflowIdFilter ?? undefined,
      status: this.statusFilter !== 'ALL' ? this.statusFilter : undefined,
      page: this.page,
      size: this.pageSize
    }).subscribe({
      next: (result) => {
        this.executions = result.content ?? [];
        this.totalPages = result.totalPages ?? 0;
        this.totalElements = result.totalElements ?? 0;
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = 'Impossible de charger les exécutions depuis le backend (ingestion-service tourne-t-il sur le port 8082 ?).';
        console.error('Failed to load workflow executions', err);
      }
    });
  }

  onStatusFilterChange(status: ExecutionStatus | 'ALL') {
    this.updateQueryParams({ status: status !== 'ALL' ? status : null });
  }

  clearWorkflowFilter() {
    this.updateQueryParams({ workflowId: null });
  }

  goToPage(newPage: number) {
    if (newPage < 0 || newPage >= this.totalPages) {
      return;
    }
    this.page = newPage;
    this.fetchExecutions();
  }

  workflowLabel(execution: WorkflowExecutionResponse): string {
    return this.workflowNamesById.get(execution.workflowId) ?? ('WF-' + execution.workflowId);
  }

  statusBadgeClass(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'text-emerald-400 bg-emerald-950/50 border border-emerald-900';
      case 'RUNNING':
      case 'ADVANCING':
        return 'text-sky-400 bg-sky-950/50 border border-sky-900';
      case 'WAITING':
        return 'text-amber-400 bg-amber-950/50 border border-amber-900';
      case 'FAILED':
        return 'text-rose-400 bg-rose-950/50 border border-rose-900';
      default:
        return 'text-gray-400 bg-gray-800/50 border border-gray-700';
    }
  }

  private updateQueryParams(changes: Record<string, string | number | null>) {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: changes,
      queryParamsHandling: 'merge'
    });
  }
}
