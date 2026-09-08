import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { NotificationsComponent } from './notifications.component';
import { NotificationService } from '../../services/notification.service';
import { NotificationLogEntry } from '../../models/notification-log.model';

/**
 * Like MonitoringComponent, this page polls on a real setInterval started
 * in ngOnInit — afterEach always calls ngOnDestroy() to clear it so nothing
 * leaks into later tests/specs.
 */
describe('NotificationsComponent', () => {
  let fixture: ComponentFixture<NotificationsComponent>;
  let component: NotificationsComponent;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;

  const logs: NotificationLogEntry[] = [
    { id: 'NOTIF-1', recipient: 'a@test.com', channel: 'EMAIL', status: 'Delivered', time: 't1' },
    { id: 'NOTIF-2', recipient: 'b@test.com', channel: 'SMS', status: 'Failed', time: 't2' }
  ];

  beforeEach(async () => {
    notificationServiceSpy = jasmine.createSpyObj<NotificationService>('NotificationService', ['getRecentLogs']);
    // A fresh copy every time: the component mutates allNotifications in
    // place (resendNotification's unshift()), so returning the same shared
    // `logs` array reference across tests would leak state between them.
    notificationServiceSpy.getRecentLogs.and.returnValue(of([...logs]));

    await TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [{ provide: NotificationService, useValue: notificationServiceSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('should fetch logs on init and select the newest entry by default', () => {
    fixture.detectChanges();

    expect(component.allNotifications).toEqual(logs);
    expect(component.selectedNotif).toEqual(logs[0]);
    expect(component.isLoading).toBeFalse();
  });

  it('should record a load error when fetching logs fails', () => {
    notificationServiceSpy.getRecentLogs.and.returnValue(throwError(() => ({ error: { message: 'backend down' } })));

    fixture.detectChanges();

    expect(component.loadError).toBe('backend down');
    expect(component.isLoading).toBeFalse();
  });

  it('should fall back to a generic error message when the backend gives none', () => {
    notificationServiceSpy.getRecentLogs.and.returnValue(throwError(() => ({})));
    fixture.detectChanges();
    expect(component.loadError).toContain('historique');
  });

  it('filteredNotifications should return everything for ALL and only matching entries otherwise', () => {
    fixture.detectChanges();

    expect(component.filteredNotifications.length).toBe(2);

    component.setFilter('FAILED');
    expect(component.filteredNotifications).toEqual([logs[1]]);
  });

  it('selectNotification should set selectedNotif', () => {
    fixture.detectChanges();
    component.selectNotification(logs[1]);
    expect(component.selectedNotif).toEqual(logs[1]);
  });

  it('toggleSendForm should flip showSendForm', () => {
    fixture.detectChanges();
    expect(component.showSendForm).toBeFalse();
    component.toggleSendForm();
    expect(component.showSendForm).toBeTrue();
  });

  it('onNotificationSent should re-fetch logs after the post-send delay', fakeAsync(() => {
    fixture.detectChanges();
    notificationServiceSpy.getRecentLogs.calls.reset();

    component.onNotificationSent();
    tick(1500);

    expect(notificationServiceSpy.getRecentLogs).toHaveBeenCalledWith(50);
  }));

  it('resendNotification should do nothing when no notification is selected', fakeAsync(() => {
    fixture.detectChanges();
    component.selectedNotif = null;

    component.resendNotification(null);
    tick(800);

    expect(component.isResending).toBeFalse();
  }));

  it('resendNotification should synthesize and prepend a replayed entry after the simulated delay', fakeAsync(() => {
    fixture.detectChanges();
    const originalCount = component.allNotifications.length;

    component.resendNotification(logs[0]);
    expect(component.isResending).toBeTrue();

    tick(800);

    expect(component.isResending).toBeFalse();
    expect(component.allNotifications.length).toBe(originalCount + 1);
    expect(component.allNotifications[0].payloadSnippet).toContain('REPLAY de NOTIF-1');
    expect(component.toastMessage).toContain('relancée');
  }));

  it('refreshLogs should re-fetch and show a toast once done', () => {
    fixture.detectChanges();
    component.refreshLogs();
    expect(component.toastMessage).toContain('rafraîchi');
  });

  it('should keep the current selection across a refresh when it is still present in the new logs', () => {
    fixture.detectChanges();
    component.selectNotification(logs[1]);

    notificationServiceSpy.getRecentLogs.and.returnValue(of([...logs]));
    component.refreshLogs();

    expect(component.selectedNotif).toEqual(logs[1]);
  });
});
