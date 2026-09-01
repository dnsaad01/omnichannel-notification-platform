import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RuleSimulatorComponent } from './rule-simulator.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('RuleSimulatorComponent', () => {
  let component: RuleSimulatorComponent;
  let fixture: ComponentFixture<RuleSimulatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RuleSimulatorComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RuleSimulatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
