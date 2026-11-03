# OrchBiz Angular website template

Reusable, data-driven public website for the current `business-landing-page`,
`services-and-pricing`, `enquiry-form` and limited `appointment-booking` module
contracts. It is not a staff dashboard or a complete appointment scheduler.

## Supported toolchain

- Node.js 24 LTS (see `.nvmrc`; Angular CLI 21 requires Node 20.19+, 22.12+,
  or 24+).
- npm 11.

Install dependencies once with `npm ci`, then run `npm start`, `npm test` or
`npm run build`. The development preview listens on port 4200 and proxies `/api`
to `http://localhost:8080`. Never disable TLS verification to work around an
npm registry certificate error.

## Configuration and preview

The Angular application fetches two public runtime assets:

- `public/assets/site.config.json`
- `public/assets/website-copy.json`

The included `example-learning-studio` files are clearly fictional demo
fixtures. Replace them with client artifacts only after both pass the OrchBiz
`site-config` and `website-copy` schema checks. Keep the two `clientSlug` values
and `api.tenantId` consistent. `contentSource` identifies the approved source
artifact; the browser loads the built asset path above, not a path outside the
site's public directory. Do not copy client data into committed demo fixtures.

The form submits `{tenantId, submittedAt, sourcePage, fields}` to
`enquiryForm.submitEndpoint`, resolved against `api.baseUrl`, and includes an
empty `_hp` honeypot field. For local development, leave `api.baseUrl` empty to use the included same-origin
proxy. For production, set it to the API origin and allow the website's exact
origin in `ORCHBIZ_ALLOWED_ORIGINS`. No admin
credential is used in the browser. The lead API tenant must already exist and
be active; its server-side required fields must match the configured form.

Client-side required, email, length and pattern validators improve feedback;
the API remains authoritative. Its current tenant configuration supports
required fields and email/phone validation, not arbitrary per-field regex or
length rules. Align the configured form with actual backend behavior.

The app checks the exact implemented module versions (currently `0.1.0`) and
fails closed for unsupported module versions or booking modes. Appointment
behavior is limited to a configured HTTPS Calendly-style external link or
manual follow-up. It does not create, confirm, or schedule appointments.
Testimonials and all other visible claims come only from the copy artifact;
empty testimonial lists omit that section.

## Verification

`npm test` runs the Angular component tests under Vitest. `npm run build` makes
the production static site under `dist/orchbiz-website-template/`.
These commands do not verify a live API, real PostgreSQL, n8n workflows,
accessibility audit, browser-to-API CORS behavior, or deployment.
