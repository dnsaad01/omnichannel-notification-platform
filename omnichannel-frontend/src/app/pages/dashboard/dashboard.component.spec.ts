import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { DashboardService } from '../../services/dashboard.service';
import { SimulatorService } from '../../services/simulator.service';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let dashboardServiceSpy: jasmine.SpyObj<DashboardService>;
  let simulatorServiceSpy: jasmine.SpyObj<SimulatorService>;

  beforeEach(async () => {
    dashboardServiceSpy = jasmine.createSpyObj<DashboardService>('DashboardService', ['getStats', 'getRecentLogs']);
    simulatorServiceSpy = jasmine.createSpyObj<SimulatorService>('SimulatorService', ['sendSingleEvent']);

    // Default happy-path responses so ngOnInit's automatic fetch (triggered
    // by the first detectChanges() below) always has something to subscribe
    // to; individual tests override these with .and.returnValue(...) before
    // re-triggering a fetch.
    dashboardServiceSpy.getStats.and.returnValue(of({ totalSent: '99', successRate: '100%', activeChannels: '3' }));
    dashboardServiceSpy.getRecentLogs.and.returnValue(of([]));
    simulatorServiceSpy.sendSingleEvent.and.returnValue(of({ event: { eventId: 'SIM-1' } }));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: DashboardService, useValue: dashboardServiceSpy },
        { provide: SimulatorService, useValue: simulatorServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  });

  it('should fetch stats and recent logs on init and replace the default placeholders', () => {
    dashboardServiceSpy.getStats.and.returnValue(of({ totalSent: '5,000', successRate: '99.1%', activeChannels: '2' }));
    dashboardServiceSpy.getRecentLogs.and.returnValue(of([{ id: 'NOTIF-1', user: 'a@b.com', channel: 'EMAIL', status: 'Delivered', time: 'now' }]));

    fixture.detectChanges();

    expect(component.stats).toEqual({ totalSent: '5,000', successRate: '99.1%', activeChannels: '2' });
    expect(component.recentLogs.length).toBe(1);
    expect(component.isRefreshing).toBeFalse();
  });

  it('should keep the existing recentLogs when the backend returns an empty list', () => {
    const originalLogs = component.recentLogs;
    dashboardServiceSpy.getRecentLogs.and.returnValue(of([]));

    fixture.detectChanges();

    expect(component.recentLogs).toBe(originalLogs);
  });

  it('should silently fall back to the current stats when getStats errors, without leaving isRefreshing stuck true', () => {
    dashboardServiceSpy.getStats.and.returnValue(throwError(() => new Error('network down')));

    fixture.detectChanges();

    expect(component.isRefreshing).toBeFalse();
  });

  it('failedDeliveries should count only entries with status Failed', () => {
    fixture.detectChanges();
    component.recentLogs = [
      { id: '1', user: 'a', channel: 'EMAIL', status: 'Delivered', time: 't' },
      { id: '2', user: 'b', channel: 'SMS', status: 'Failed', time: 't' },
      { id: '3', user: 'c', channel: 'PUSH', status: 'Failed', time: 't' }
    ];

    expect(component.failedDeliveries).toBe(2);
  });

  it('refreshDashboard should re-fetch data and show a toast message', () => {
    fixture.detectChanges();
    dashboardServiceSpy.getStats.calls.reset();

    component.refreshDashboard();

    expect(dashboardServiceSpy.getStats).toHaveBeenCalledTimes(1);
    expect(component.toastMessage).toContain('actualisées');
  });

  it('triggerSimulatedDispatch should call SimulatorService, show a toast with the event id, and re-fetch dashboard data', () => {
    fixture.detectChanges();
    dashboardServiceSpy.getStats.calls.reset();

    component.triggerSimulatedDispatch();

    expect(simulatorServiceSpy.sendSingleEvent).toHaveBeenCalled();
    expect(component.toastMessage).toContain('SIM-1');
    expect(dashboardServiceSpy.getStats).toHaveBeenCalledTimes(1);
  });
});
