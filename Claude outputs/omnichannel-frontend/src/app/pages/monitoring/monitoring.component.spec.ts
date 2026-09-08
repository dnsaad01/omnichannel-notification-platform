import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MonitoringComponent } from './monitoring.component';
import { SimulatorService } from '../../services/simulator.service';
import { MonitoringService } from '../../services/monitoring.service';
import { InfrastructureHealthResponse } from '../../models/monitoring.model';

/**
 * MonitoringComponent polls fetchHealth() every 5s via a real setInterval
 * started in ngOnInit (see POLL_INTERVAL_MS). Left running, that interval
 * would keep firing against a torn-down fixture/spy in every later test in
 * the whole Karma run — afterEach always calls ngOnDestroy() (which
 * clearIntervals it for real, regardless of whether a given `it()` used
 * fakeAsync) so nothing leaks past this describe block.
 */
describe('MonitoringComponent', () => {
  let fixture: ComponentFixture<MonitoringComponent>;
  let component: MonitoringComponent;
  let simulatorServiceSpy: jasmine.SpyObj<SimulatorService>;
  let monitoringServiceSpy: jasmine.SpyObj<MonitoringService>;

  const healthyResponse: InfrastructureHealthResponse = {
    healthy: true,
    database: { up: true, message: 'OK' },
    kafka: { up: true, message: 'OK' },
    redis: { up: true, message: 'OK' }
  };

  beforeEach(async () => {
    simulatorServiceSpy = jasmine.createSpyObj<SimulatorService>('SimulatorService', [
      'getStatus', 'stopSimulation', 'startSimulation', 'sendSingleEvent', 'sendBatchEvents'
    ]);
    monitoringServiceSpy = jasmine.createSpyObj<MonitoringService>('MonitoringService', ['getHealth']);

    simulatorServiceSpy.getStatus.and.returnValue(of({ active: false, ratePerSecond: 2, totalSent: 0, totalErrors: 0, targetTopic: 'notification.ingestion', timestamp: '' }));
    monitoringServiceSpy.getHealth.and.returnValue(of(healthyResponse));

    await TestBed.configureTestingModule({
      imports: [MonitoringComponent],
      providers: [
        { provide: SimulatorService, useValue: simulatorServiceSpy },
        { provide: MonitoringService, useValue: monitoringServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MonitoringComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('should fetch health and simulator status on init', () => {
    fixture.detectChanges();

    expect(component.health).toEqual(healthyResponse);
    expect(component.healthLoadError).toBeNull();
    expect(component.isHealthLoading).toBeFalse();
  });

  it('should record a load error and leave the previous health snapshot untouched when the health check fails', () => {
    fixture.detectChanges();
    expect(component.health).toEqual(healthyResponse);

    monitoringServiceSpy.getHealth.and.returnValue(throwError(() => ({ error: { message: 'DB unreachable' } })));
    component.fetchHealth();

    expect(component.healthLoadError).toBe('DB unreachable');
    expect(component.health).toEqual(healthyResponse);
  });

  it('should fall back to a generic error message when the backend gives none', () => {
    fixture.detectChanges();
    monitoringServiceSpy.getHealth.and.returnValue(throwError(() => ({})));

    component.fetchHealth();

    expect(component.healthLoadError).toContain('infrastructure');
  });

  it('refreshHealth should show a toast and re-fetch both health and simulator status', () => {
    fixture.detectChanges();
    monitoringServiceSpy.getHealth.calls.reset();
    simulatorServiceSpy.getStatus.calls.reset();

    component.refreshHealth();

    expect(component.toastMessage).toContain('rafraîchi');
    expect(monitoringServiceSpy.getHealth).toHaveBeenCalledTimes(1);
    expect(simulatorServiceSpy.getStatus).toHaveBeenCalledTimes(1);
  });

  it('purgeRedisCache should show a confirmation toast', () => {
    fixture.detectChanges();
    component.purgeRedisCache();
    expect(component.toastMessage).toContain('Cache Redis purgé');
  });

  it('openDlqModal/closeDlqModal should toggle isDlqModalOpen', () => {
    fixture.detectChanges();
    expect(component.isDlqModalOpen).toBeFalse();

    component.openDlqModal();
    expect(component.isDlqModalOpen).toBeTrue();

    component.closeDlqModal();
    expect(component.isDlqModalOpen).toBeFalse();
  });

  it('replayDlqMessages should empty the DLQ list and reset the count', () => {
    fixture.detectChanges();
    expect(component.dlqMessages.length).toBeGreaterThan(0);

    component.replayDlqMessages();

    expect(component.dlqMessages).toEqual([]);
    expect(component.dlqCount).toBe('0 messages');
    expect(component.toastMessage).toContain('réinjectés');
  });

  it('deleteDlqMessage should remove only the targeted message and update the count', () => {
    fixture.detectChanges();
    const originalCount = component.dlqMessages.length;

    component.deleteDlqMessage('DLQ-901');

    expect(component.dlqMessages.find(m => m.id === 'DLQ-901')).toBeUndefined();
    expect(component.dlqMessages.length).toBe(originalCount - 1);
    expect(component.dlqCount).toBe(`${originalCount - 1} messages`);
  });

  it('refreshSimulatorStatus should default simRate to 2 when the backend omits ratePerSecond', () => {
    simulatorServiceSpy.getStatus.and.returnValue(of({ active: false, ratePerSecond: 0, totalSent: 0, totalErrors: 0, targetTopic: 't', timestamp: '' }));

    fixture.detectChanges();

    expect(component.simRate).toBe(2);
  });

  it('toggleSimulation should stop the simulator when it is currently active', () => {
    fixture.detectChanges();
    component.simulatorStatus.active = true;
    simulatorServiceSpy.stopSimulation.and.returnValue(of({ status: 'SUCCESS' }));

    component.toggleSimulation();

    expect(simulatorServiceSpy.stopSimulation).toHaveBeenCalled();
    expect(component.simActionAlert).toContain('ARRÊTÉ');
    expect(component.isSimLoading).toBeFalse();
  });

  it('toggleSimulation should start the simulator at simRate when it is currently inactive', () => {
    fixture.detectChanges();
    component.simulatorStatus.active = false;
    component.simRate = 7;
    simulatorServiceSpy.startSimulation.and.returnValue(of({ status: 'SUCCESS' }));

    component.toggleSimulation();

    expect(simulatorServiceSpy.startSimulation).toHaveBeenCalledWith(7);
    expect(component.simActionAlert).toContain('DÉMARRÉ');
  });

  it('sendSingleSimulatedEvent should call the simulator service and set an alert with the event id', () => {
    fixture.detectChanges();
    simulatorServiceSpy.sendSingleEvent.and.returnValue(of({ event: { eventId: 'SIM-77' } }));

    component.sendSingleSimulatedEvent();

    expect(component.simActionAlert).toContain('SIM-77');
    expect(component.isSimLoading).toBeFalse();
  });

  it('sendBatchSimulatedEvents should call the simulator service with the given count', () => {
    fixture.detectChanges();
    simulatorServiceSpy.sendBatchEvents.and.returnValue(of({ count: 25 }));

    component.sendBatchSimulatedEvents(25);

    expect(simulatorServiceSpy.sendBatchEvents).toHaveBeenCalledWith(25);
    expect(component.simActionAlert).toContain('25');
  });

  it('should poll fetchHealth again after the 5s interval elapses', fakeAsync(() => {
    fixture.detectChanges();
    monitoringServiceSpy.getHealth.calls.reset();

    tick(5000);

    expect(monitoringServiceSpy.getHealth).toHaveBeenCalledTimes(1);

    component.ngOnDestroy();
    tick(5000);
    // No further calls once destroyed — proves the interval was really cleared.
    expect(monitoringServiceSpy.getHealth).toHaveBeenCalledTimes(1);
  }));
});
