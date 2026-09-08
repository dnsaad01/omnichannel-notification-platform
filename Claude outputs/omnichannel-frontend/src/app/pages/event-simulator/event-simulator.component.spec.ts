import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { EventSimulatorComponent } from './event-simulator.component';
import { BusinessEventService } from '../../services/business-event.service';

describe('EventSimulatorComponent', () => {
  let fixture: ComponentFixture<EventSimulatorComponent>;
  let component: EventSimulatorComponent;
  let businessEventServiceSpy: jasmine.SpyObj<BusinessEventService>;

  beforeEach(async () => {
    businessEventServiceSpy = jasmine.createSpyObj<BusinessEventService>('BusinessEventService', ['publish']);

    await TestBed.configureTestingModule({
      imports: [EventSimulatorComponent],
      providers: [{ provide: BusinessEventService, useValue: businessEventServiceSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(EventSimulatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should start with the first preset selected and its JSON payload pre-filled', () => {
    expect(component.selectedPreset).toBe('CART_ABANDONED');
    expect(component.payloadText).toContain('cartValue');
    expect(component.useCustomEventType).toBeFalse();
  });

  it('onPresetChange should load the newly selected preset payload when not in custom mode', () => {
    component.selectedPreset = 'ORDER_SHIPPED';
    component.onPresetChange();

    expect(component.payloadText).toContain('carrier');
  });

  it('onToggleCustom should clear the payload to {} when switching into custom mode, and restore the preset when switching back', () => {
    component.useCustomEventType = true;
    component.onToggleCustom();
    expect(component.payloadText).toBe('{}');

    component.useCustomEventType = false;
    component.onToggleCustom();
    expect(component.payloadText).toContain('cartValue');
  });

  it('effectiveEventType should return the trimmed custom event type when custom mode is on', () => {
    component.useCustomEventType = true;
    component.customEventType = '  CUSTOM_EVENT  ';

    expect(component.effectiveEventType).toBe('CUSTOM_EVENT');
  });

  it('effectiveEventType should return the selected preset when custom mode is off', () => {
    component.selectedPreset = 'USER_SIGNED_UP';
    expect(component.effectiveEventType).toBe('USER_SIGNED_UP');
  });

  it('publish should reject a blank custom event type without calling the service', () => {
    component.useCustomEventType = true;
    component.customEventType = '   ';

    component.publish();

    expect(component.payloadError).toContain('obligatoire');
    expect(businessEventServiceSpy.publish).not.toHaveBeenCalled();
  });

  it('publish should reject invalid JSON in the payload without calling the service', () => {
    component.payloadText = '{ not valid json';

    component.publish();

    expect(component.payloadError).toContain('JSON valide');
    expect(businessEventServiceSpy.publish).not.toHaveBeenCalled();
  });

  it('publish should treat a blank payload as an empty object rather than failing validation', () => {
    businessEventServiceSpy.publish.and.returnValue(of({ status: 'PUBLISHED', eventType: 'CART_ABANDONED', payload: {} }));
    component.payloadText = '   ';

    component.publish();

    expect(businessEventServiceSpy.publish.calls.mostRecent().args[0].payload).toEqual({});
  });

  it('publish should call the service with the parsed payload and record a SUCCESS history entry on success', () => {
    businessEventServiceSpy.publish.and.returnValue(of({ status: 'PUBLISHED', eventType: 'CART_ABANDONED', payload: { cartValue: 89.9 } }));

    component.publish();

    expect(businessEventServiceSpy.publish).toHaveBeenCalledWith({ eventType: 'CART_ABANDONED', payload: jasmine.objectContaining({ cartValue: 89.9 }) });
    expect(component.isPublishing).toBeFalse();
    expect(component.history.length).toBe(1);
    expect(component.history[0].status).toBe('SUCCESS');
    expect(component.toastMessage).toContain('publié avec succès');
  });

  it('publish should record an ERROR history entry with the backend message on failure', () => {
    businessEventServiceSpy.publish.and.returnValue(throwError(() => ({ error: { message: 'No matching workflow' } })));

    component.publish();

    expect(component.isPublishing).toBeFalse();
    expect(component.history[0].status).toBe('ERROR');
    expect(component.history[0].message).toBe('No matching workflow');
  });

  it('publish should fall back to a generic error message when the backend gives none', () => {
    businessEventServiceSpy.publish.and.returnValue(throwError(() => ({})));

    component.publish();

    expect(component.history[0].message).toContain('Échec de la publication');
  });

  it('clearHistory should empty the history list', () => {
    businessEventServiceSpy.publish.and.returnValue(of({ status: 'PUBLISHED', eventType: 'CART_ABANDONED', payload: {} }));
    component.publish();
    expect(component.history.length).toBe(1);

    component.clearHistory();

    expect(component.history).toEqual([]);
  });
});
