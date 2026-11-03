export interface SiteField {
  name: string;
  label: string;
  type: 'text' | 'email' | 'tel' | 'select' | 'textarea' | 'date' | 'checkbox';
  required: boolean;
  options?: string[];
  validation?: {
    minLength?: number;
    maxLength?: number;
    pattern?: string;
  };
}

export interface SiteConfig {
  clientSlug: string;
  enabledModules: Array<{ id: string; version: string }>;
  theme: {
    primaryColor: string;
    secondaryColor: string;
    neutralColor?: string;
    backgroundColor?: string;
    headingFont?: string;
    bodyFont?: string;
  };
  assets?: {
    logoUrl?: string;
    usesPlaceholders?: boolean;
  };
  contentSource: string;
  enquiryForm: {
    fields: SiteField[];
    submitEndpoint: string;
    consentText?: string;
  };
  booking: {
    type:
      | 'Google Calendar'
      | 'Calendly Link'
      | 'Built-in Slot Request'
      | 'Manual Follow-up Only';
    url?: string;
  };
  api: {
    baseUrl: string;
    tenantId: string;
  };
}

export interface WebsiteCopy {
  clientSlug: string;
  hero: {
    headline: string;
    subheadline: string;
    primaryCta: string;
    secondaryCta?: string;
  };
  services: Array<{
    name: string;
    description: string;
    duration?: string;
    price?: string;
    audience?: string;
  }>;
  differentiators: Array<{ title: string; description: string }>;
  testimonials: Array<{ quote: string; attribution: string }>;
  faq: Array<{ question: string; answer: string }>;
  about?: { heading?: string; body?: string };
  enquiryCta: {
    headline: string;
    body?: string;
    buttonLabel: string;
    successMessage?: string;
  };
  footer: {
    businessName: string;
    location?: string;
    hours?: string;
    contactEmail?: string;
    contactPhone?: string;
    legalNote?: string;
  };
  seo?: { title?: string; description?: string };
}

export function isSiteConfig(value: unknown): value is SiteConfig {
  if (!isRecord(value) || !isRecord(value['theme']) || !isRecord(value['enquiryForm'])
      || !isRecord(value['api']) || !isRecord(value['booking']) || !Array.isArray(value['enabledModules'])) {
    return false;
  }
  const form = value['enquiryForm'];
  const theme = value['theme'];
  return typeof value['clientSlug'] === 'string'
    && typeof value['contentSource'] === 'string'
    && Array.isArray(form['fields'])
    && typeof form['submitEndpoint'] === 'string'
    && typeof value['api']['tenantId'] === 'string'
    && typeof value['api']['baseUrl'] === 'string'
    && typeof value['booking']['type'] === 'string'
    && /^#[\da-f]{6}$/i.test(String(theme['primaryColor']))
    && /^#[\da-f]{6}$/i.test(String(theme['secondaryColor']))
    && value['enabledModules'].every((item) => isRecord(item)
      && typeof item['id'] === 'string' && typeof item['version'] === 'string');
}

export function isWebsiteCopy(value: unknown): value is WebsiteCopy {
  return isRecord(value)
    && typeof value['clientSlug'] === 'string'
    && isRecord(value['hero'])
    && Array.isArray(value['services'])
    && Array.isArray(value['differentiators'])
    && Array.isArray(value['testimonials'])
    && Array.isArray(value['faq'])
    && isRecord(value['enquiryCta'])
    && isRecord(value['footer']);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
