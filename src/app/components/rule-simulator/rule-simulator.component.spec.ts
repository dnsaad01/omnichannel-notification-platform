import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RuleSimulatorComponent } from './rule-simulator.component';

describe('RuleSimulatorComponent', () => {
  let component: RuleSimulatorComponent;
  let fixture: ComponentFixture<RuleSimulatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RuleSimulatorComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RuleSimulatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
