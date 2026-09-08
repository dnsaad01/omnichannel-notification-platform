import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TemplateRequest, TemplateResponse, TemplateService } from './template.service';

describe('TemplateService', () => {
  let service: TemplateService;
  let httpMock: HttpTestingController;

  const BASE_URL = 'http://localhost:8082/api/templates';

  const aTemplate: TemplateResponse = {
    id: 1,
    name: 'Welcome Email',
    channel: 'EMAIL',
    subject: 'Welcome!',
    body: 'Hello {{name}}',
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(TemplateService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET the template list from /api/templates', () => {
    let actual: TemplateResponse[] | undefined;
    service.getAllTemplates().subscribe(r => (actual = r));

    const req = httpMock.expectOne(BASE_URL);
    expect(req.request.method).toBe('GET');
    req.flush([aTemplate]);

    expect(actual).toEqual([aTemplate]);
  });

  it('should GET a single template by id', () => {
    let actual: TemplateResponse | undefined;
    service.getTemplateById(1).subscribe(r => (actual = r));

    const req = httpMock.expectOne(`${BASE_URL}/1`);
    expect(req.request.method).toBe('GET');
    req.flush(aTemplate);

    expect(actual).toEqual(aTemplate);
  });

  it('should POST a new template with the exact request body given', () => {
    const request: TemplateRequest = { name: 'New', channel: 'EMAIL', body: 'body' };

    service.saveTemplate(request).subscribe();

    const req = httpMock.expectOne(BASE_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(aTemplate);
  });

  it('should PUT an update to /api/templates/{id}', () => {
    const request: TemplateRequest = { name: 'Renamed', channel: 'EMAIL', body: 'body' };

    service.updateTemplate(1, request).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ ...aTemplate, name: 'Renamed' });
  });

  it('should DELETE a template by id', () => {
    let completed = false;
    service.deleteTemplate(1).subscribe(() => (completed = true));

    const req = httpMock.expectOne(`${BASE_URL}/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(completed).toBeTrue();
  });

  it('should POST a test-send payload to /api/templates/send', () => {
    const payload = { templateId: 1, recipientId: 'usr_1001' };

    service.testSendTemplate(payload).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/send`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ status: 'SUCCESS' });
  });

  it('should propagate a backend validation error from saveTemplate to the caller', () => {
    let captured: number | undefined;
    service.saveTemplate({ name: '', channel: 'EMAIL', body: '' }).subscribe({
      next: () => fail('expected an error, not a success'),
      error: err => (captured = err.status)
    });

    httpMock.expectOne(BASE_URL).flush({ message: 'name is required' }, { status: 400, statusText: 'Bad Request' });

    expect(captured).toBe(400);
  });
});
