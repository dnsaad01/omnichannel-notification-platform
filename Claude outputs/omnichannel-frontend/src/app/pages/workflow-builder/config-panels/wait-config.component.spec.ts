import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WaitConfigComponent } from './wait-config.component';

describe('WaitConfigComponent', () => {
  let fixture: ComponentFixture<WaitConfigComponent>;
  let component: WaitConfigComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [WaitConfigComponent] });
    fixture = TestBed.createComponent(WaitConfigComponent);
    component = fixture.componentInstance;
  });

  it('should default duration to 1 and unit to HOURS when config is empty', () => {
    component.config = {};
    expect(component.duration).toBe(1);
    expect(component.unit).toBe('HOURS');
  });

  it('should read duration/unit from the given config', () => {
    component.config = { duration: 30, unit: 'MINUTES' };
    expect(component.duration).toBe(30);
    expect(component.unit).toBe('MINUTES');
  });

  it('setting duration should coerce the value to a number and emit', () => {
    component.config = { unit: 'DAYS' };
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));

    component.duration = '5' as any;

    expect(emitted).toEqual({ unit: 'DAYS', duration: 5 });
  });

  it('setting unit should emit the full config with unit replaced', () => {
    component.config = { duration: 2 };
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));

    component.unit = 'DAYS';

    expect(emitted).toEqual({ duration: 2, unit: 'DAYS' });
  });

  it('should offer exactly the three supported ChronoUnit values', () => {
    expect(component.units).toEqual(['MINUTES', 'HOURS', 'DAYS']);
  });
});
