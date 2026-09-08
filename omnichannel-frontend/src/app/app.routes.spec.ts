import { Route } from '@angular/router';
import { routes } from './app.routes';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { TemplatesComponent } from './pages/templates/templates.component';
import { WorkflowsComponent } from './pages/workflows/workflows.component';
import { WorkflowBuilderComponent } from './pages/workflow-builder/workflow-builder.component';
import { WorkflowExecutionsComponent } from './pages/workflow-executions/workflow-executions.component';
import { WorkflowExecutionDetailComponent } from './pages/workflow-execution-detail/workflow-execution-detail.component';
import { EventSimulatorComponent } from './pages/event-simulator/event-simulator.component';
import { NotificationsComponent } from './pages/notifications/notifications.component';
import { StatisticsComponent } from './pages/statistics/statistics.component';
import { MonitoringComponent } from './pages/monitoring/monitoring.component';

/**
 * Route config for a lazy-loaded, single-page app is easy to silently break
 * (a typo'd path, a loadComponent() pointing at the wrong module) without
 * any compiler error catching it — this suite resolves every lazy import
 * for real and checks it lands on the component the path implies.
 */
describe('app.routes', () => {
  function find(path: string): Route {
    const route = routes.find(r => r.path === path);
    if (!route) {
      throw new Error(`No route registered for path "${path}"`);
    }
    return route;
  }

  it('should redirect the empty path to dashboard', () => {
    const root = find('');
    expect(root.redirectTo).toBe('dashboard');
    expect(root.pathMatch).toBe('full');
  });

  it('should redirect any unmatched path to dashboard', () => {
    const wildcard = find('**');
    expect(wildcard.redirectTo).toBe('dashboard');
  });

  it('should be the very last entry, so it never shadows a real route', () => {
    expect(routes[routes.length - 1].path).toBe('**');
  });

  it('should register exactly one route per known path, with no accidental duplicates', () => {
    const paths = routes.map(r => r.path);
    expect(new Set(paths).size).toBe(paths.length);
  });

  const expectations: Array<[string, any]> = [
    ['dashboard', DashboardComponent],
    ['templates', TemplatesComponent],
    ['workflows', WorkflowsComponent],
    ['workflows/new', WorkflowBuilderComponent],
    ['workflows/executions', WorkflowExecutionsComponent],
    ['workflows/executions/:id', WorkflowExecutionDetailComponent],
    ['workflows/:id/edit', WorkflowBuilderComponent],
    ['event-simulator', EventSimulatorComponent],
    ['notifications', NotificationsComponent],
    ['statistics', StatisticsComponent],
    ['monitoring', MonitoringComponent]
  ];

  expectations.forEach(([path, expectedComponent]) => {
    it(`"${path}" should lazily resolve to ${expectedComponent.name}`, async () => {
      const route = find(path);
      expect(route.loadComponent).toBeDefined();
      const resolved = await (route.loadComponent as () => Promise<any>)();
      expect(resolved).toBe(expectedComponent);
    });
  });
});
