import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationConfigComponent } from './notification-config.component';

describe('NotificationConfigComponent', () => {
  let fixture: ComponentFixture<NotificationConfigComponent>;
  let component: NotificationConfigComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [NotificationConfigComponent] });
    fixture = TestBed.createComponent(NotificationConfigComponent);
    component = fixture.componentInstance;
  });

  it('should default templateId to null, channel/recipientPath to empty string when config is empty', () => {
    component.config = {};
    expect(component.templateId).toBeNull();
    expect(component.channel).toBe('');
    expect(component.recipientPath).toBe('');
  });

  it('should read each field from the given config', () => {
    component.config = { templateId: 3, channel: 'SMS', recipientPath: 'context.phone' };
    expect(component.templateId).toBe(3);
    expect(component.channel).toBe('SMS');
    expect(component.recipientPath).toBe('context.phone');
  });

  it('setting templateId should coerce a string value to a number and emit', () => {
    component.config = {};
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));

    component.templateId = '7';

    expect(emitted).toEqual({ templateId: 7 });
  });

  it('setting templateId to an empty string or null should emit templateId: null', () => {
    component.config = { channel: 'EMAIL' };
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));

    component.templateId = '';

    expect(emitted).toEqual({ channel: 'EMAIL', templateId: null });
  });

  it('setting channel should emit the full config with only channel replaced', () => {
    component.config = { templateId: 1 };
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));

    component.channel = 'PUSH';

    expect(emitted).toEqual({ templateId: 1, channel: 'PUSH' });
  });

  it('setting recipientPath should emit the full config with only recipientPath replaced', () => {
    component.config = {};
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));

    component.recipientPath = 'context.userId';

    expect(emitted).toEqual({ recipientPath: 'context.userId' });
  });

  it('selectedTemplate should find the template matching templateId, or undefined if none matches', () => {
    component.templates = [{ id: 1, name: 'Welcome', channel: 'EMAIL' }, { id: 2, name: 'Reminder', channel: 'SMS' }];
    component.config = { templateId: 2 };
    expect(component.selectedTemplate).toEqual({ id: 2, name: 'Reminder', channel: 'SMS' });

    component.config = { templateId: 999 };
    expect(component.selectedTemplate).toBeUndefined();
  });
});
