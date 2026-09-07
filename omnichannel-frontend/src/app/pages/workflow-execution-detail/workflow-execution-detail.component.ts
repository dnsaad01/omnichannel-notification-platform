import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { WorkflowExecutionService } from '../../services/workflow-execution.service';
import { WorkflowService } from '../../services/workflow.service';
import { WorkflowExecutionResponse } from '../../models/workflow.model';

/** Statuses that can still change on their own — worth auto-polling. */
const LIVE_STATUSES = ['RUNNING', 'WAITING', 'ADVANCING'];
const POLL_INTERVAL_MS = 5000;

/**
 * Read-only execution detail — backed by GET /api/workflow-executions/{id}
 * (WorkflowExecutionController, Phase 1), which is the only endpoint that
 * returns the full Timeline (logs). Auto-refreshes every 5s while the
 * execution is still RUNNING/WAITING/ADVANCING, so a WAIT node resolving in
 * the background (WorkflowWaitScheduler) shows up here without a manual
 * refresh — useful for actually watching the engine work during a demo.
 */
@Component({
  selector: 'app-workflow-execution-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './workflow-execution-detail.component.html'
})
export class WorkflowExecutionDetailComponent implements OnInit, OnDestroy {
  private executionService = inject(WorkflowExecutionService);
  private workflowService = inject(WorkflowService);
  private route = inject(ActivatedRoute);

  execution: WorkflowExecutionResponse | null = null;
  workflowName: string | null = null;
  prettyContext: string | null = null;

  isLoading = false;
  errorMessage: string | null = null;

  private executionId!: number;
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  ngOnInit() {
    this.executionId = Number(this.route.snapshot.paramMap.get('id'));
    this.fetchExecution();
  }

  ngOnDestroy() {
    this.stopPolling();
  }

  fetchExecution() {
    this.isLoading = true;
    this.errorMessage = null;

    this.executionService.getById(this.executionId).subscribe({
      next: (data) => {
        this.execution = data;
        this.prettyContext = this.formatContext(data.contextJson);
        this.isLoading = false;

        if (LIVE_STATUSES.includes(data.status)) {
          this.startPolling();
        } else {
          this.stopPolling();
        }

        if (!this.workflowName) {
          this.workflowService.getWorkflowById(data.workflowId).subscribe({
            next: (wf) => (this.workflowName = wf.name),
            error: () => (this.workflowName = null)
          });
        }
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = 'Impossible de charger cette exécution depuis le backend (ingestion-service tourne-t-il sur le port 8082 ?).';
        console.error('Failed to load execution detail', err);
      }
    });
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

  logDotClass(level: string): string {
    return level === 'ERROR' ? 'bg-rose-500' : 'bg-orange-500';
  }

  private startPolling() {
    if (this.pollHandle) {
      return;
    }
    this.pollHandle = setInterval(() => this.fetchExecution(), POLL_INTERVAL_MS);
  }

  private stopPolling() {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  private formatContext(contextJson: string | null): string | null {
    if (!contextJson) {
      return null;
    }
    try {
      return JSON.stringify(JSON.parse(contextJson), null, 2);
    } catch {
      return contextJson;
    }
  }
}
