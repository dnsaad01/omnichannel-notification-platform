import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TriggerConfigComponent } from './trigger-config.component';

describe('TriggerConfigComponent', () => {
  let fixture: ComponentFixture<TriggerConfigComponent>;
  let component: TriggerConfigComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TriggerConfigComponent] });
    fixture = TestBed.createComponent(TriggerConfigComponent);
    component = fixture.componentInstance;
  });

  it('should default eventType to an empty string when config is empty', () => {
    component.config = {};
    expect(component.eventType).toBe('');
  });

  it('should read eventType from the given config', () => {
    component.config = { eventType: 'CART_ABANDONED' };
    expect(component.eventType).toBe('CART_ABANDONED');
  });

  it('setting eventType should emit the full config with eventType replaced', () => {
    component.config = { someOtherField: 'x' };
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));

    component.eventType = 'ORDER_CREATED';

    expect(emitted).toEqual({ someOtherField: 'x', eventType: 'ORDER_CREATED' });
  });
});
