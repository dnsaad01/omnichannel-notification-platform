import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'templates',
    loadComponent: () => import('./pages/templates/templates.component').then(m => m.TemplatesComponent)
  },
  {
    path: 'workflows',
    loadComponent: () => import('./pages/workflows/workflows.component').then(m => m.WorkflowsComponent)
  },
  {
    path: 'workflows/new',
    loadComponent: () => import('./pages/workflow-builder/workflow-builder.component').then(m => m.WorkflowBuilderComponent)
  },
  {
    path: 'workflows/executions',
    loadComponent: () => import('./pages/workflow-executions/workflow-executions.component').then(m => m.WorkflowExecutionsComponent)
  },
  {
    path: 'workflows/executions/:id',
    loadComponent: () => import('./pages/workflow-execution-detail/workflow-execution-detail.component').then(m => m.WorkflowExecutionDetailComponent)
  },
  {
    path: 'workflows/:id/edit',
    loadComponent: () => import('./pages/workflow-builder/workflow-builder.component').then(m => m.WorkflowBuilderComponent)
  },
  {
    path: 'event-simulator',
    loadComponent: () => import('./pages/event-simulator/event-simulator.component').then(m => m.EventSimulatorComponent)
  },
  {
    path: 'notifications',
    loadComponent: () => import('./pages/notifications/notifications.component').then(m => m.NotificationsComponent)
  },
  {
    path: 'statistics',
    loadComponent: () => import('./pages/statistics/statistics.component').then(m => m.StatisticsComponent)
  },
  {
    path: 'monitoring',
    loadComponent: () => import('./pages/monitoring/monitoring.component').then(m => m.MonitoringComponent)
  },
  { path: '**', redirectTo: 'dashboard' }
];
