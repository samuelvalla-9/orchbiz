import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { App } from './app';

const config = {
  clientSlug: 'fictional-demo',
  enabledModules: [
    { id: 'business-landing-page', version: '0.1.0' },
    { id: 'services-and-pricing', version: '0.1.0' },
    { id: 'enquiry-form', version: '0.1.0' }
  ],
  theme: { primaryColor: '#123456', secondaryColor: '#abcdef' },
  contentSource: '../content/website-copy.json',
  enquiryForm: {
    submitEndpoint: '/api/leads',
    fields: [
      { name: 'full_name', label: 'Name', type: 'text', required: true, validation: { minLength: 2 } },
      { name: 'email', label: 'Email', type: 'email', required: true }
    ]
  },
  booking: { type: 'Manual Follow-up Only' },
  api: { baseUrl: 'http://localhost:8080', tenantId: 'fictional-demo' }
};

const copy = {
  clientSlug: 'fictional-demo',
  hero: { headline: 'A fictional sample', subheadline: 'Demo copy only.', primaryCta: 'Enquire' },
  services: [{ name: 'Example service', description: 'Fictional.' }],
  differentiators: [],
  testimonials: [],
  faq: [],
  enquiryCta: { headline: 'Get in touch', buttonLabel: 'Send enquiry', successMessage: 'Received.' },
  footer: { businessName: 'Fictional Demo' }
};

describe('App', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function loadPage(siteConfig: unknown = config, websiteCopy: unknown = copy): void {
    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.expectOne('/assets/site.config.json').flush(siteConfig as Record<string, unknown>);
    http.expectOne('/assets/website-copy.json').flush(websiteCopy as Record<string, unknown>);
    fixture.detectChanges();
  }

  function fill(id: string, value: string): void {
    const input = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  it('renders schema-provided landing and service content', () => {
    loadPage();
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('h1')?.textContent).toContain(copy.hero.headline);
    expect(page.querySelector('.service-card h3')?.textContent).toContain('Example service');
  });

  it('does not send an incomplete form and shows required-field errors', () => {
    loadPage();
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Name is required.');
    http.expectNone((request) => request.method === 'POST');
  });

  it('rejects invalid email in the browser without sending a request', () => {
    loadPage();
    fill('full_name', 'Sam Sample');
    fill('email', 'not-an-email');
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Enter a valid email address.');
    http.expectNone((request) => request.method === 'POST');
  });

  it('posts the documented lead shape and shows success', () => {
    loadPage();
    fill('full_name', 'Sam Sample');
    fill('email', 'sam@example.test');
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const request = http.expectOne('http://localhost:8080/api/leads');
    expect(request.request.body).toMatchObject({
      tenantId: 'fictional-demo',
      sourcePage: '/',
      fields: { full_name: 'Sam Sample', email: 'sam@example.test', _hp: '' }
    });
    expect(request.request.body.submittedAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    request.flush({ leadId: 'demo-id', duplicate: false }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('Received.');
    expect((fixture.nativeElement.querySelector('#full_name') as HTMLInputElement).value).toBe('');
  });

  it('forwards a filled honeypot field so the API can discard bot submissions', () => {
    loadPage();
    fill('full_name', 'Sam Sample');
    fill('email', 'sam@example.test');
    (fixture.nativeElement.querySelector('#website-url') as HTMLInputElement).value = 'https://spam.example';
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    const request = http.expectOne('http://localhost:8080/api/leads');
    expect(request.request.body.fields._hp).toBe('https://spam.example');
    request.flush({ accepted: true }, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('#website-url') as HTMLInputElement).value).toBe('');
  });

  it('keeps the visible service link text in its accessible name', () => {
    loadPage();
    const link = fixture.nativeElement.querySelector('.service-card .text-link') as HTMLAnchorElement;
    expect(link.getAttribute('aria-label')).toContain('Ask about this service');
  });

  it('reports a duplicate as accepted without treating it as an error', () => {
    loadPage();
    fill('full_name', 'Sam Sample');
    fill('email', 'sam@example.test');
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    http.expectOne('http://localhost:8080/api/leads').flush(
      { leadId: 'demo-id', duplicate: true },
      { status: 200, statusText: 'OK' }
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('already received');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows a clear rate-limit response', () => {
    loadPage();
    fill('full_name', 'Sam Sample');
    fill('email', 'sam@example.test');
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    http.expectOne('http://localhost:8080/api/leads').flush(
      { error: 'rate_limited' },
      { status: 429, statusText: 'Too Many Requests' }
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('too many attempts');
  });

  it('explains validation and inactive-tenant API failures', () => {
    loadPage();
    fill('full_name', 'Sam Sample');
    fill('email', 'sam@example.test');
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    http.expectOne('http://localhost:8080/api/leads').flush(
      { error: 'validation_failed' },
      { status: 400, statusText: 'Bad Request' }
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('review the form');

    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    http.expectOne('http://localhost:8080/api/leads').flush(
      { error: 'tenant_not_accepting_leads' },
      { status: 403, statusText: 'Forbidden' }
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('not accepting submissions');
  });

  it('reports an unavailable API without claiming success', () => {
    loadPage();
    fill('full_name', 'Sam Sample');
    fill('email', 'sam@example.test');
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    http.expectOne('http://localhost:8080/api/leads').error(new ProgressEvent('network error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('could not reach');
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
  });

  it('refuses mismatched content and unsupported module versions', () => {
    loadPage({ ...config, enabledModules: [{ id: 'business-landing-page', version: '2.0.0' }] });
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('configuration is invalid');
  });
});
