import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError, Subject } from 'rxjs';
import { NotificationsComponent } from './notifications.component';
import { NotificationService, PagedNotificationLogs } from '../../services/notification.service';
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

  /** Builds a PagedNotificationLogs response. Defaults describe a single,
   *  complete page (totalElements === content.length, totalPages 1) so
   *  most tests don't need to think about pagination metadata at all;
   *  pagination-specific tests override page/totalElements/totalPages
   *  explicitly. */
  function pagedResult(content: NotificationLogEntry[], overrides: Partial<PagedNotificationLogs> = {}): PagedNotificationLogs {
    return {
      content,
      page: 0,
      size: 50,
      totalElements: content.length,
      totalPages: content.length > 0 ? 1 : 0,
      ...overrides
    };
  }

  beforeEach(async () => {
    notificationServiceSpy = jasmine.createSpyObj<NotificationService>('NotificationService', ['getRecentLogsPage', 'resendNotification']);
    // A fresh copy every time: fetchLogs() reassigns allNotifications wholesale
    // on every response, but returning the same shared `logs` array reference
    // across tests would still let one test's mutation of the mock leak into
    // another's assertions.
    notificationServiceSpy.getRecentLogsPage.and.returnValue(of(pagedResult([...logs])));

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

  it('should fetch the first page on init and select the newest entry by default', () => {
    fixture.detectChanges();

    expect(notificationServiceSpy.getRecentLogsPage).toHaveBeenCalledWith(0, 50);
    expect(component.allNotifications).toEqual(logs);
    expect(component.selectedNotif).toEqual(logs[0]);
    expect(component.isLoading).toBeFalse();
  });

  it('should record a load error when fetching logs fails', () => {
    notificationServiceSpy.getRecentLogsPage.and.returnValue(throwError(() => ({ error: { message: 'backend down' } })));

    fixture.detectChanges();

    expect(component.loadError).toBe('backend down');
    expect(component.isLoading).toBeFalse();
  });

  it('should fall back to a generic error message when the backend gives none', () => {
    notificationServiceSpy.getRecentLogsPage.and.returnValue(throwError(() => ({})));
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

  it('onNotificationSent should re-fetch the current page after the post-send delay', fakeAsync(() => {
    fixture.detectChanges();
    notificationServiceSpy.getRecentLogsPage.calls.reset();

    component.onNotificationSent();
    tick(1500);

    expect(notificationServiceSpy.getRecentLogsPage).toHaveBeenCalledWith(0, 50);
  }));

  // --- Pagination ---

  it('should expose totalElements and totalPages from the backend response', () => {
    notificationServiceSpy.getRecentLogsPage.and.returnValue(
      of(pagedResult([...logs], { page: 0, totalElements: 312, totalPages: 7 }))
    );

    fixture.detectChanges();

    expect(component.totalElements).toBe(312);
    expect(component.totalPages).toBe(7);
    expect(component.currentPage).toBe(0);
  });

  it('goToNextPage should advance currentPage and re-fetch that page', () => {
    notificationServiceSpy.getRecentLogsPage.and.returnValue(
      of(pagedResult([...logs], { page: 0, totalElements: 150, totalPages: 3 }))
    );
    fixture.detectChanges();

    notificationServiceSpy.getRecentLogsPage.and.returnValue(
      of(pagedResult([logs[1]], { page: 1, totalElements: 150, totalPages: 3 }))
    );
    component.goToNextPage();

    expect(component.currentPage).toBe(1);
    expect(notificationServiceSpy.getRecentLogsPage).toHaveBeenCalledWith(1, 50);
  });

  it('goToNextPage should do nothing once already on the last page', () => {
    notificationServiceSpy.getRecentLogsPage.and.returnValue(
      of(pagedResult([...logs], { page: 2, totalElements: 150, totalPages: 3 }))
    );
    fixture.detectChanges();
    component.currentPage = 2;
    notificationServiceSpy.getRecentLogsPage.calls.reset();

    component.goToNextPage();

    expect(component.currentPage).toBe(2);
    expect(notificationServiceSpy.getRecentLogsPage).not.toHaveBeenCalled();
  });

  it('goToNextPage should do nothing when the table is empty (totalPages 0)', () => {
    notificationServiceSpy.getRecentLogsPage.and.returnValue(of(pagedResult([], { totalElements: 0, totalPages: 0 })));
    fixture.detectChanges();
    notificationServiceSpy.getRecentLogsPage.calls.reset();

    component.goToNextPage();

    expect(component.currentPage).toBe(0);
    expect(notificationServiceSpy.getRecentLogsPage).not.toHaveBeenCalled();
  });

  it('goToPreviousPage should decrement currentPage and re-fetch that page', () => {
    notificationServiceSpy.getRecentLogsPage.and.returnValue(
      of(pagedResult([...logs], { page: 1, totalElements: 150, totalPages: 3 }))
    );
    fixture.detectChanges();
    component.currentPage = 1;

    notificationServiceSpy.getRecentLogsPage.and.returnValue(
      of(pagedResult([...logs], { page: 0, totalElements: 150, totalPages: 3 }))
    );
    component.goToPreviousPage();

    expect(component.currentPage).toBe(0);
    expect(notificationServiceSpy.getRecentLogsPage).toHaveBeenCalledWith(0, 50);
  });

  it('goToPreviousPage should do nothing when already on the first page', () => {
    fixture.detectChanges();
    notificationServiceSpy.getRecentLogsPage.calls.reset();

    component.goToPreviousPage();

    expect(component.currentPage).toBe(0);
    expect(notificationServiceSpy.getRecentLogsPage).not.toHaveBeenCalled();
  });

  it('goToNextPage should not fire a request while a fetch is already in flight, even mid-table', () => {
    fixture.detectChanges(); // settles the initial page-0 fetch
    component.currentPage = 1;
    component.totalPages = 3;
    component.isLoading = true;
    notificationServiceSpy.getRecentLogsPage.calls.reset();

    component.goToNextPage();

    expect(notificationServiceSpy.getRecentLogsPage).not.toHaveBeenCalled();
    expect(component.currentPage).toBe(1);
  });

  it('goToPreviousPage should not fire a request while a fetch is already in flight, even mid-table', () => {
    fixture.detectChanges(); // settles the initial page-0 fetch
    component.currentPage = 2;
    component.totalPages = 3;
    component.isLoading = true;
    notificationServiceSpy.getRecentLogsPage.calls.reset();

    component.goToPreviousPage();

    expect(notificationServiceSpy.getRecentLogsPage).not.toHaveBeenCalled();
    expect(component.currentPage).toBe(2);
  });

  // --- Resend ---

  it('resendNotification should do nothing when no notification is selected', () => {
    fixture.detectChanges();
    component.selectedNotif = null;

    component.resendNotification(null);

    expect(component.isResending).toBeFalse();
    expect(notificationServiceSpy.resendNotification).not.toHaveBeenCalled();
  });

  it('resendNotification should ignore a second click while a resend is already in flight', () => {
    fixture.detectChanges();
    notificationServiceSpy.resendNotification.and.returnValue(new Subject<any>());

    component.resendNotification(logs[0]);
    component.resendNotification(logs[0]);

    expect(notificationServiceSpy.resendNotification).toHaveBeenCalledTimes(1);
  });

  it('resendNotification should POST to the backend, toast, and refresh the log immediately and again after the post-resend delay', fakeAsync(() => {
    fixture.detectChanges();
    notificationServiceSpy.getRecentLogsPage.calls.reset();
    const resend$ = new Subject<any>();
    notificationServiceSpy.resendNotification.and.returnValue(resend$);

    component.resendNotification(logs[0]);

    expect(component.isResending).toBeTrue();
    expect(notificationServiceSpy.resendNotification).toHaveBeenCalledWith('NOTIF-1');
    expect(notificationServiceSpy.getRecentLogsPage).not.toHaveBeenCalled();

    resend$.next({ originalId: 'NOTIF-1', recipient: 'a@test.com', channel: 'EMAIL', message: 'ok' });
    resend$.complete();

    // Settles right away — no manual page refresh needed to see the toast
    // or trigger the first re-fetch.
    expect(component.isResending).toBeFalse();
    expect(component.toastMessage).toContain('relancée');
    expect(notificationServiceSpy.getRecentLogsPage).toHaveBeenCalledTimes(1);

    tick(1500);

    // Second, delayed re-fetch (same POST_SEND_REFRESH_DELAY_MS pattern as
    // onNotificationSent) to pick up the new notification_logs row once the
    // channel consumer has actually written it.
    expect(notificationServiceSpy.getRecentLogsPage).toHaveBeenCalledTimes(2);
  }));

  it('resendNotification should surface a backend error, stop the spinner, and not refresh', () => {
    fixture.detectChanges();
    notificationServiceSpy.getRecentLogsPage.calls.reset();
    notificationServiceSpy.resendNotification.and.returnValue(
      throwError(() => ({ error: { message: 'Notification log not found: NOTIF-1' } }))
    );

    component.resendNotification(logs[0]);

    expect(component.isResending).toBeFalse();
    expect(component.toastMessage).toBe('Notification log not found: NOTIF-1');
    expect(notificationServiceSpy.getRecentLogsPage).not.toHaveBeenCalled();
  });

  it('resendNotification should fall back to a generic error message when the backend gives none', () => {
    fixture.detectChanges();
    notificationServiceSpy.resendNotification.and.returnValue(throwError(() => ({})));

    component.resendNotification(logs[0]);

    expect(component.toastMessage).toContain('Échec de la relance');
  });

  it('refreshLogs should re-fetch and show a toast once done', () => {
    fixture.detectChanges();
    component.refreshLogs();
    expect(component.toastMessage).toContain('rafraîchi');
  });

  it('should keep the current selection across a refresh when it is still present in the new logs', () => {
    fixture.detectChanges();
    component.selectNotification(logs[1]);

    notificationServiceSpy.getRecentLogsPage.and.returnValue(of(pagedResult([...logs])));
    component.refreshLogs();

    expect(component.selectedNotif).toEqual(logs[1]);
  });
});
