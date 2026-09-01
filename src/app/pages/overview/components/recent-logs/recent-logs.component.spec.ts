import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RecentLogsComponent } from './recent-logs.component';

describe('RecentLogsComponent', () => {
  let component: RecentLogsComponent;
  let fixture: ComponentFixture<RecentLogsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecentLogsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RecentLogsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
