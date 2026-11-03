import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { FormRecord, ReactiveFormsModule, UntypedFormControl, ValidatorFn, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { isSiteConfig, isWebsiteCopy, SiteConfig, SiteField, WebsiteCopy } from './site-model';

@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private readonly http = inject(HttpClient);

  private readonly siteConfig = signal<SiteConfig | null>(null);
  private readonly websiteCopy = signal<WebsiteCopy | null>(null);
  private readonly isLoading = signal(true);
  private readonly configErrorMessage = signal('');
  private readonly isSubmitting = signal(false);
  private readonly submissionErrorMessage = signal('');
  private readonly submissionNoticeMessage = signal('');
  private readonly wasSubmitted = signal(false);
  private readonly honeypot = viewChild<ElementRef<HTMLInputElement>>('honeypot');

  protected get config(): SiteConfig | null {
    return this.siteConfig();
  }

  protected get copy(): WebsiteCopy | null {
    return this.websiteCopy();
  }

  protected get loading(): boolean {
    return this.isLoading();
  }

  protected get configError(): string {
    return this.configErrorMessage();
  }

  protected get submitting(): boolean {
    return this.isSubmitting();
  }

  protected get submissionError(): string {
    return this.submissionErrorMessage();
  }

  protected get submissionNotice(): string {
    return this.submissionNoticeMessage();
  }

  protected get submitted(): boolean {
    return this.wasSubmitted();
  }

  protected form = new FormRecord<UntypedFormControl>({});

  ngOnInit(): void {
    forkJoin([
      this.http.get<unknown>('/assets/site.config.json'),
      this.http.get<unknown>('/assets/website-copy.json')
    ]).subscribe({
      next: ([config, copy]) => {
        if (!isSiteConfig(config) || !isWebsiteCopy(copy) || config.clientSlug !== copy.clientSlug
          || config.api.tenantId !== config.clientSlug || !this.hasPublicModule(config, 'business-landing-page')
          || !this.hasSupportedVersions(config) || !this.hasSupportedBooking(config)) {
          this.configErrorMessage.set('The website configuration is invalid or does not match its content.');
          this.isLoading.set(false);
          return;
        }
        this.siteConfig.set(config);
        this.websiteCopy.set(copy);
        this.setTheme(config);
        this.setPageMetadata(copy);
        this.createForm(config.enquiryForm.fields);
        this.isLoading.set(false);
      },
      error: () => {
        this.configErrorMessage.set('Website content is unavailable. Please try again later.');
        this.isLoading.set(false);
      }
    });
  }

  protected hasModule(id: string): boolean {
    return this.config?.enabledModules.some((module) => module.id === id) ?? false;
  }

  protected fieldError(field: SiteField): string {
    const control = this.form.controls[field.name];
    if (!control || !control.touched || !control.errors) {
      return '';
    }
    if (control.hasError('required')) {
      return `${field.label} is required.`;
    }
    if (control.hasError('email')) {
      return 'Enter a valid email address.';
    }
    if (control.hasError('minlength')) {
      return `${field.label} is too short.`;
    }
    if (control.hasError('maxlength')) {
      return `${field.label} is too long.`;
    }
    if (control.hasError('pattern')) {
      return `${field.label} has an invalid format.`;
    }
    return `${field.label} is invalid.`;
  }

  protected submit(): void {
    if (!this.config || this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSubmitting.set(true);
    this.submissionErrorMessage.set('');
    this.submissionNoticeMessage.set('');
    this.wasSubmitted.set(false);
    // The API silently discards submissions whose `_hp` spam-trap value is non-empty.
    const fields = { ...this.form.getRawValue(), _hp: this.honeypot()?.nativeElement.value ?? '' };
    const payload = {
      tenantId: this.config.api.tenantId,
      submittedAt: new Date().toISOString(),
      sourcePage: window.location.pathname,
      fields
    };

    let endpoint: string;
    try {
      endpoint = this.resolveEndpoint(this.config);
    } catch {
      this.isSubmitting.set(false);
      this.submissionErrorMessage.set('The enquiry service is not configured. Please contact the website operator.');
      return;
    }

    this.http.post<{ duplicate?: boolean }>(endpoint, payload).subscribe({
      next: (response) => {
        this.wasSubmitted.set(true);
        this.isSubmitting.set(false);
        if (response.duplicate) {
          this.submissionNoticeMessage.set('We already received your enquiry and have added your latest details.');
        } else {
          this.resetForm();
        }
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        this.submissionErrorMessage.set(this.messageFor(error));
      }
    });
  }

  private createForm(fields: SiteField[]): void {
    for (const field of fields) {
      const validators: ValidatorFn[] = [];
      if (field.required) {
        validators.push(field.type === 'checkbox' ? Validators.requiredTrue : Validators.required);
      }
      if (field.type === 'email') {
        validators.push(Validators.email);
      }
      if (field.validation?.minLength !== undefined) {
        validators.push(Validators.minLength(field.validation.minLength));
      }
      if (field.validation?.maxLength !== undefined) {
        validators.push(Validators.maxLength(field.validation.maxLength));
      }
      if (field.validation?.pattern) {
        validators.push(Validators.pattern(field.validation.pattern));
      }
      this.form.addControl(field.name, new UntypedFormControl(
        field.type === 'checkbox' ? false : '',
        { validators, nonNullable: true }
      ));
    }
  }

  private resetForm(): void {
    const emptyValues = Object.fromEntries(
      (this.config?.enquiryForm.fields ?? []).map((field) => [field.name, field.type === 'checkbox' ? false : ''])
    );
    this.form.reset(emptyValues);
    const honeypot = this.honeypot();
    if (honeypot) {
      honeypot.nativeElement.value = '';
    }
  }

  private resolveEndpoint(config: SiteConfig): string {
    const endpoint = config.enquiryForm.submitEndpoint;
    if (/^https?:\/\//i.test(endpoint)) {
      return endpoint;
    }
    const baseUrl = config.api.baseUrl;
    if (baseUrl.includes('${')) {
      throw new Error('Replace the API_BASE_URL placeholder in the deployed site configuration.');
    }
    return new URL(endpoint, baseUrl || window.location.origin).toString();
  }

  private messageFor(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'We could not reach the enquiry service. Check your connection and try again.';
    }
    if (error.status === 400) {
      return 'Some details need attention. Please review the form and try again.';
    }
    if (error.status === 403) {
      return 'This enquiry form is not accepting submissions right now.';
    }
    if (error.status === 429) {
      return 'There have been too many attempts. Please wait a moment before trying again.';
    }
    return 'We could not submit your enquiry right now. Please try again later.';
  }

  private hasPublicModule(config: SiteConfig, id: string): boolean {
    return config.enabledModules.some((module) => module.id === id);
  }

  protected bookingLink(): string | null {
    const value = this.config?.booking.url;
    if (!value) {
      return null;
    }
    try {
      const url = new URL(value);
      return url.protocol === 'https:' || (url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname))
        ? url.toString()
        : null;
    } catch {
      return null;
    }
  }

  private hasSupportedVersions(config: SiteConfig): boolean {
    const supported: Record<string, string> = {
      'business-landing-page': '0.1.0',
      'services-and-pricing': '0.1.0',
      'enquiry-form': '0.1.0',
      'appointment-booking': '0.1.0'
    };
    return config.enabledModules.every((module) => supported[module.id] === module.version);
  }

  private hasSupportedBooking(config: SiteConfig): boolean {
    return config.booking.type === 'Manual Follow-up Only'
      || (config.booking.type === 'Calendly Link' && this.validBookingUrl(config.booking.url));
  }

  private validBookingUrl(value?: string): boolean {
    if (!value) {
      return false;
    }
    try {
      const url = new URL(value);
      return url.protocol === 'https:' || (url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname));
    } catch {
      return false;
    }
  }

  private setTheme(config: SiteConfig): void {
    const root = document.documentElement;
    root.style.setProperty('--brand-primary', config.theme.primaryColor);
    root.style.setProperty('--brand-secondary', config.theme.secondaryColor);
    root.style.setProperty('--brand-neutral', config.theme.neutralColor ?? '#253047');
    root.style.setProperty('--brand-background', config.theme.backgroundColor ?? '#f7f8fb');
    root.style.setProperty('--heading-font', this.safeFont(config.theme.headingFont, 'Georgia, serif'));
    root.style.setProperty('--body-font', this.safeFont(config.theme.bodyFont, 'Arial, sans-serif'));
  }

  private safeFont(value: string | undefined, fallback: string): string {
    return value && /^[\w\s"',-]+$/.test(value) ? value : fallback;
  }

  private setPageMetadata(copy: WebsiteCopy): void {
    if (copy.seo?.title) {
      document.title = copy.seo.title;
    } else {
      document.title = copy.footer.businessName;
    }
    if (copy.seo?.description) {
      let description = document.querySelector<HTMLMetaElement>('meta[name="description"]');
      if (!description) {
        description = document.createElement('meta');
        description.name = 'description';
        document.head.append(description);
      }
      description.content = copy.seo.description;
    }
  }
}
