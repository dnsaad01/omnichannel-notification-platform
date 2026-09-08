import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GatewayConfigComponent } from './gateway-config.component';

describe('GatewayConfigComponent', () => {
  let fixture: ComponentFixture<GatewayConfigComponent>;
  let component: GatewayConfigComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [GatewayConfigComponent] });
    fixture = TestBed.createComponent(GatewayConfigComponent);
    component = fixture.componentInstance;
  });

  it('should default variable to empty string, operator to equals, and value to empty string when config is empty', () => {
    component.config = {};
    expect(component.variable).toBe('');
    expect(component.operator).toBe('equals');
    expect(component.value).toBe('');
  });

  it('should read each field from the given config', () => {
    component.config = { variable: 'cartValue', operator: 'greaterThan', value: '100' };
    expect(component.variable).toBe('cartValue');
    expect(component.operator).toBe('greaterThan');
    expect(component.value).toBe('100');
  });

  it('setting variable should emit the full config with only variable replaced', () => {
    component.config = { operator: 'equals', value: 'x' };
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));

    component.variable = 'orderId';

    expect(emitted).toEqual({ operator: 'equals', value: 'x', variable: 'orderId' });
  });

  it('setting operator should emit the full config with only operator replaced', () => {
    component.config = { variable: 'v' };
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));

    component.operator = 'exists';

    expect(emitted).toEqual({ variable: 'v', operator: 'exists' });
  });

  it('setting value should emit the full config with only value replaced', () => {
    component.config = { variable: 'v', operator: 'equals' };
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));

    component.value = '42';

    expect(emitted).toEqual({ variable: 'v', operator: 'equals', value: '42' });
  });

  it('should list all 5 supported operators', () => {
    expect(component.operators.map(o => o.value)).toEqual(['equals', 'notEquals', 'greaterThan', 'lessThan', 'exists']);
  });
});
