import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MonitoringComponent } from './monitoring.component';
import { SimulatorService } from '../../services/simulator.service';
import { MonitoringService } from '../../services/monitoring.service';
import { DlqMessage, InfrastructureHealthResponse } from '../../models/monitoring.model';

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

  const sampleDlqMessages: DlqMessage[] = [
    { id: '901', recipient: 'invalid_email_format.com', channel: 'EMAIL', errorReason: 'SMTP 550 Invalid Recipient', timestamp: '10:14:22' },
    { id: '902', recipient: '+212000000000', channel: 'SMS', errorReason: 'Twilio Unreachable Carrier', timestamp: '11:05:01' }
  ];

  beforeEach(async () => {
    simulatorServiceSpy = jasmine.createSpyObj<SimulatorService>('SimulatorService', [
      'getStatus', 'stopSimulation', 'startSimulation', 'sendSingleEvent', 'sendBatchEvents'
    ]);
    monitoringServiceSpy = jasmine.createSpyObj<MonitoringService>('MonitoringService', [
      'getHealth', 'getDlqMessages', 'deleteDlqMessage', 'replayDlqMessages'
    ]);

    simulatorServiceSpy.getStatus.and.returnValue(of({ active: false, ratePerSecond: 2, totalSent: 0, totalErrors: 0, targetTopic: 'notification.ingestion', timestamp: '' }));
    monitoringServiceSpy.getHealth.and.returnValue(of(healthyResponse));
    // Component fetches the real DLQ list from the backend on init (and
    // again on every modal open / refresh) instead of starting from a
    // hardcoded array — this stub is what used to be the fake 3-entry list.
    monitoringServiceSpy.getDlqMessages.and.returnValue(of(sampleDlqMessages));

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

  it('refreshHealth should show a toast and re-fetch health, simulator status and the DLQ list', () => {
    fixture.detectChanges();
    monitoringServiceSpy.getHealth.calls.reset();
    simulatorServiceSpy.getStatus.calls.reset();
    monitoringServiceSpy.getDlqMessages.calls.reset();

    component.refreshHealth();

    expect(component.toastMessage).toContain('rafraîchi');
    expect(monitoringServiceSpy.getHealth).toHaveBeenCalledTimes(1);
    expect(simulatorServiceSpy.getStatus).toHaveBeenCalledTimes(1);
    expect(monitoringServiceSpy.getDlqMessages).toHaveBeenCalledTimes(1);
  });

  it('purgeRedisCache should show a confirmation toast', () => {
    fixture.detectChanges();
    component.purgeRedisCache();
    expect(component.toastMessage).toContain('Cache Redis purgé');
  });

  it('should load the real DLQ list on init, driving both dlqMessages and dlqCount', () => {
    fixture.detectChanges();

    expect(component.dlqMessages).toEqual(sampleDlqMessages);
    expect(component.dlqCount).toBe('2 messages');
    expect(component.isDlqLoading).toBeFalse();
  });

  it('should record a load error when the DLQ list cannot be fetched', () => {
    monitoringServiceSpy.getDlqMessages.and.returnValue(throwError(() => ({ error: { message: 'DB unreachable' } })));

    fixture.detectChanges();

    expect(component.dlqLoadError).toBe('DB unreachable');
    expect(component.isDlqLoading).toBeFalse();
  });

  it('openDlqModal/closeDlqModal should toggle isDlqModalOpen and re-fetch the DLQ list on open', () => {
    fixture.detectChanges();
    expect(component.isDlqModalOpen).toBeFalse();
    monitoringServiceSpy.getDlqMessages.calls.reset();

    component.openDlqModal();
    expect(component.isDlqModalOpen).toBeTrue();
    expect(monitoringServiceSpy.getDlqMessages).toHaveBeenCalledTimes(1);

    component.closeDlqModal();
    expect(component.isDlqModalOpen).toBeFalse();
  });

  it('replayDlqMessages should call the backend, show the real replayed count, and refresh the list', () => {
    fixture.detectChanges();
    monitoringServiceSpy.replayDlqMessages.and.returnValue(of({ replayedCount: 2 }));
    // After a successful replay the backend's list is now empty — assert
    // the component reflects that re-fetched state, not a locally-cleared
    // array (which is exactly what made the original bug possible).
    monitoringServiceSpy.getDlqMessages.and.returnValue(of([]));

    component.replayDlqMessages();

    expect(monitoringServiceSpy.replayDlqMessages).toHaveBeenCalled();
    expect(component.dlqMessages).toEqual([]);
    expect(component.dlqCount).toBe('0 messages');
    expect(component.toastMessage).toContain('réinjectés');
  });

  it('replayDlqMessages should surface a toast on failure without clearing the list', () => {
    fixture.detectChanges();
    monitoringServiceSpy.replayDlqMessages.and.returnValue(throwError(() => ({ error: { message: 'Kafka unreachable' } })));

    component.replayDlqMessages();

    expect(component.toastMessage).toBe('Kafka unreachable');
    expect(component.dlqMessages).toEqual(sampleDlqMessages);
  });

  it('deleteDlqMessage should call the backend for the targeted id and refresh the list from the server', () => {
    fixture.detectChanges();
    monitoringServiceSpy.deleteDlqMessage.and.returnValue(of(undefined));
    const afterDelete = sampleDlqMessages.filter(m => m.id !== '901');
    monitoringServiceSpy.getDlqMessages.and.returnValue(of(afterDelete));

    component.deleteDlqMessage('901');

    expect(monitoringServiceSpy.deleteDlqMessage).toHaveBeenCalledWith('901');
    // The list is whatever the backend now says it is — not a locally
    // filtered copy of the previous array — which is the actual fix for
    // "deleted messages reappear on refresh": there is no local-only state
    // left that a refresh could diverge from.
    expect(component.dlqMessages.find(m => m.id === '901')).toBeUndefined();
    expect(component.dlqCount).toBe(`${afterDelete.length} messages`);
  });

  it('deleteDlqMessage should surface a toast on failure without touching the list', () => {
    fixture.detectChanges();
    monitoringServiceSpy.deleteDlqMessage.and.returnValue(throwError(() => ({ error: { message: 'Not found' } })));

    component.deleteDlqMessage('999');

    expect(component.toastMessage).toBe('Not found');
    expect(component.dlqMessages).toEqual(sampleDlqMessages);
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
