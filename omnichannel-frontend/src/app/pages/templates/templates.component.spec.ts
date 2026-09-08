import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TemplatesComponent } from './templates.component';
import { TemplateResponse, TemplateService } from '../../services/template.service';

describe('TemplatesComponent', () => {
  let fixture: ComponentFixture<TemplatesComponent>;
  let component: TemplatesComponent;
  let templateServiceSpy: jasmine.SpyObj<TemplateService>;

  const aTemplate: TemplateResponse = {
    id: 1,
    name: 'Welcome',
    channel: 'EMAIL',
    subject: 'Hi',
    body: 'Hello {{name}}',
    status: 'ACTIVE'
  };

  beforeEach(async () => {
    templateServiceSpy = jasmine.createSpyObj<TemplateService>('TemplateService', [
      'getAllTemplates', 'saveTemplate', 'updateTemplate', 'deleteTemplate', 'testSendTemplate', 'getTemplateById'
    ]);
    templateServiceSpy.getAllTemplates.and.returnValue(of([aTemplate]));

    await TestBed.configureTestingModule({
      imports: [TemplatesComponent],
      providers: [{ provide: TemplateService, useValue: templateServiceSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(TemplatesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should fetch the template list on init', () => {
    expect(component.templates).toEqual([aTemplate]);
    expect(component.isLoading).toBeFalse();
    expect(component.view).toBe('list');
  });

  it('should default templates to an empty array when the backend returns null/undefined', () => {
    templateServiceSpy.getAllTemplates.and.returnValue(of(null as any));
    component.fetchTemplates();
    expect(component.templates).toEqual([]);
  });

  it('should record an error message when fetching the list fails', () => {
    templateServiceSpy.getAllTemplates.and.returnValue(throwError(() => new Error('down')));
    component.fetchTemplates();
    expect(component.errorMessage).toContain('Impossible de charger');
    expect(component.isLoading).toBeFalse();
  });

  it('createNew should reset the form and switch to the form view', () => {
    component.view = 'list';
    component.createNew();

    expect(component.view).toBe('form');
    expect(component.editingId).toBeNull();
    expect(component.form).toEqual({ name: '', channel: 'EMAIL', subject: '', body: '', status: 'ACTIVE' });
  });

  it('edit should populate the form from the given template and switch to the form view', () => {
    component.edit(aTemplate);

    expect(component.view).toBe('form');
    expect(component.editingId).toBe(1);
    expect(component.form).toEqual({ name: 'Welcome', channel: 'EMAIL', subject: 'Hi', body: 'Hello {{name}}', status: 'ACTIVE' });
  });

  it('cancel should return to the list view', () => {
    component.view = 'form';
    component.cancel();
    expect(component.view).toBe('list');
  });

  it('save should reject a blank name or body without calling the service', () => {
    component.form = { name: '', channel: 'EMAIL', body: '' };
    component.save();

    expect(templateServiceSpy.saveTemplate).not.toHaveBeenCalled();
    expect(component.toastMessage).toContain('obligatoires');
  });

  it('save should POST a new template via saveTemplate when not editing, then return to the list', () => {
    component.editingId = null;
    component.form = { name: 'New', channel: 'EMAIL', body: 'Body' };
    templateServiceSpy.saveTemplate.and.returnValue(of(aTemplate));

    component.save();

    expect(templateServiceSpy.saveTemplate).toHaveBeenCalledWith(component.form);
    expect(templateServiceSpy.updateTemplate).not.toHaveBeenCalled();
    expect(component.isSaving).toBeFalse();
    expect(component.view).toBe('list');
    expect(component.toastMessage).toContain('créé');
  });

  it('save should PUT an update via updateTemplate when editingId is set', () => {
    component.editingId = 1;
    component.form = { name: 'Renamed', channel: 'EMAIL', body: 'Body' };
    templateServiceSpy.updateTemplate.and.returnValue(of(aTemplate));

    component.save();

    expect(templateServiceSpy.updateTemplate).toHaveBeenCalledWith(1, component.form);
    expect(component.toastMessage).toContain('mis à jour');
  });

  it('save should surface the backend error message and stay in the form view on failure', () => {
    component.createNew();
    component.form = { name: 'New', channel: 'EMAIL', body: 'Body' };
    templateServiceSpy.saveTemplate.and.returnValue(throwError(() => ({ error: { message: 'name already exists' } })));

    component.save();

    expect(component.isSaving).toBeFalse();
    expect(component.errorMessage).toBe('name already exists');
    // save()'s error handler never touches `view` — it's expected to stay
    // wherever the user already was (the form) rather than being bounced
    // back to the list, unlike the success path which explicitly returns
    // to 'list'.
    expect(component.view).toBe('form');
  });

  it('remove should call deleteTemplate, show a toast, and refresh the list', () => {
    templateServiceSpy.deleteTemplate.and.returnValue(of(undefined));
    templateServiceSpy.getAllTemplates.calls.reset();

    component.remove(aTemplate);

    expect(templateServiceSpy.deleteTemplate).toHaveBeenCalledWith(1);
    expect(component.toastMessage).toContain('supprimé');
    expect(templateServiceSpy.getAllTemplates).toHaveBeenCalledTimes(1);
  });

  it('remove should surface a backend error rather than silently failing', () => {
    templateServiceSpy.deleteTemplate.and.returnValue(throwError(() => ({ error: { message: 'in use' } })));

    component.remove(aTemplate);

    expect(component.errorMessage).toBe('in use');
  });

  it('onTestSend should call testSendTemplate with a synthesized payload and confirm success', () => {
    component.form = { name: 'x', channel: 'SMS', body: 'test body' };
    templateServiceSpy.testSendTemplate.and.returnValue(of({ status: 'SUCCESS' }));

    component.onTestSend();

    expect(templateServiceSpy.testSendTemplate).toHaveBeenCalledWith({ channel: 'SMS', recipient: '+212600000000', message: 'test body' });
    expect(component.isTesting).toBeFalse();
    expect(component.toastMessage).toContain('envoyé avec succès');
  });

  it('onTestSend should still show a submission toast even if the request errors', () => {
    templateServiceSpy.testSendTemplate.and.returnValue(throwError(() => new Error('down')));

    component.onTestSend();

    expect(component.isTesting).toBeFalse();
    expect(component.toastMessage).toContain('soumise au bus de messages');
  });

  it('insertVariable should append the variable to the current body', () => {
    component.form.body = 'Hello';
    component.insertVariable('{{name}}');
    expect(component.form.body).toBe('Hello {{name}} ');
  });

  it('statusBadgeClass should return distinct classes for ACTIVE, DRAFT, and any other status', () => {
    expect(component.statusBadgeClass('ACTIVE')).toContain('emerald');
    expect(component.statusBadgeClass('DRAFT')).toContain('amber');
    expect(component.statusBadgeClass('ARCHIVED')).toContain('gray');
  });
});
