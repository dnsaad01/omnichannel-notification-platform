import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { StatisticsComponent } from './statistics.component';
import { StatisticsService } from '../../services/statistics.service';
import { StatisticsResponse } from '../../models/statistics.model';

describe('StatisticsComponent', () => {
  let fixture: ComponentFixture<StatisticsComponent>;
  let component: StatisticsComponent;
  let statisticsServiceSpy: jasmine.SpyObj<StatisticsService>;

  const response: StatisticsResponse = {
    totalSent: '1,000',
    deliveryRate: '95%',
    openRate: '40%',
    clickRate: 'Non suivi',
    channels: [{ name: 'EMAIL', sent: '800', delivered: '760', rate: '95%' }]
  };

  beforeEach(async () => {
    statisticsServiceSpy = jasmine.createSpyObj<StatisticsService>('StatisticsService', ['getStatistics']);
    statisticsServiceSpy.getStatistics.and.returnValue(of(response));

    await TestBed.configureTestingModule({
      imports: [StatisticsComponent],
      providers: [{ provide: StatisticsService, useValue: statisticsServiceSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(StatisticsComponent);
    component = fixture.componentInstance;
  });

  it('should start with placeholder dashes before the first response lands', () => {
    expect(component.globalStats.totalSent).toBe('—');
    expect(component.channels).toEqual([]);
  });

  it('should populate globalStats and channels from the backend response on init', () => {
    fixture.detectChanges();

    expect(component.globalStats).toEqual({ totalSent: '1,000', deliveryRate: '95%', openRate: '40%', clickRate: 'Non suivi' });
    expect(component.channels).toEqual(response.channels);
    expect(component.isLoading).toBeFalse();
    expect(component.loadError).toBeNull();
  });

  it('should record an error message and stop loading when the backend call fails', () => {
    statisticsServiceSpy.getStatistics.and.returnValue(throwError(() => ({ error: { message: 'Backend unreachable' } })));

    fixture.detectChanges();

    expect(component.loadError).toBe('Backend unreachable');
    expect(component.isLoading).toBeFalse();
  });

  it('should fall back to a generic error message when the backend gives none', () => {
    statisticsServiceSpy.getStatistics.and.returnValue(throwError(() => ({})));
    fixture.detectChanges();
    expect(component.loadError).toContain('statistiques');
  });
});
