import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EndConfigComponent } from './end-config.component';

describe('EndConfigComponent', () => {
  let fixture: ComponentFixture<EndConfigComponent>;
  let component: EndConfigComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [EndConfigComponent] });
    fixture = TestBed.createComponent(EndConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and accept a config input without exposing any configurable fields', () => {
    expect(component).toBeTruthy();
    expect(component.config).toEqual({});
  });

  it('should render its static explanatory text', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('COMPLETED');
  });
});
