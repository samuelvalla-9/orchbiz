# OrchBiz: Personal-Device Agent Handoff

Prepared: 2026-11-04.

For terminal-by-terminal manual verification on any developer device, use the
[manual test runbook](manual-test-runbook.md). Run heavy suites outside the agent
conversation to reduce token usage; share only concise results or failure excerpts.

## 1. Mission and immediate objective

The owner is launching a business that sells reusable business automation systems to clients. OrchBiz is the delivery workspace for that business: an agent-assisted process for turning a client's questionnaire into scoped requirements, architecture, website content, a configured website, automations, QA evidence and handover documents.

This is **not yet a complete product or an unattended delivery platform**. The repository has working deterministic tooling and a partially implemented runtime. The reusable public website template, a fictional browser-to-API run and a committed mocked-API browser regression suite exist. The next objective is to reproduce the backend stack and verify live n8n executions, then implement the remaining package capabilities.

The flagship package is `starter-lead-to-appointment`, aimed initially at coaching/training institutes and small local service businesses. Its approved stack is:

- Angular parameterized website templates.
- Spring Boot REST API.
- PostgreSQL storage.
- n8n workflows.
- Narrowly scoped, consent-gated LLM calls.

Do not replace this stack, expand package scope or introduce a new orchestration platform just to complete the demo.

**Important correction to the previous conversation:** the statement that "everything that can be built on this device" was done was too broad. One agreed batch was completed. Scheduling, reminders, staff authentication, a dashboard, deployment operations and other work remain; some of this could also be developed without Docker or npm. Treat the repository and evidence below as the source of truth, not that completion claim.

## 2. Source checkout and continuation point

| Item | Value at handoff |
|---|---|
| Repository | `samuelvalla-9/orchbiz` |
| Working branch | `GH-Copilot_Setup` |
| Latest inspected commit | `aad7e65` |
| Remote status when inspected | Local branch aligned with `origin/GH-Copilot_Setup` |
| Worktree when inspected | Dirty: existing documentation/catalogue edits plus uncommitted browser-suite and CI changes; preserve them |
| Machine inspected for this update | Non-personal Windows 11 workstation; this is not the personal-device inventory |
| Tools on inspected machine | Java 24.0.1, Maven 4.0.0-rc-4, system Node 25.9.0/npm 11.6.2, Angular CLI 21.2.11; installed Edge available |
| Runtime/toolchain limitation | Docker is unavailable. The template declares Node 24 LTS; system Node 25.9.0 is outside that engine range |
| Historical network issue | npm previously encountered `SELF_SIGNED_CERT_IN_CHAIN`; npm registry access succeeded during this update without TLS workarounds |

This checkout was inspected at `aad7e65`; the local branch matched `origin/GH-Copilot_Setup`, but the worktree was not clean. Fetch the latest branch and preserve all current modifications rather than resetting or overwriting them. The code-level checks below were run on the non-personal workstation and do not establish the personal device's software versions or runtime readiness. Read `docs\system-operating-model-and-mvp.md` for the owner workflow and proposed narrower pilot scope; that proposal does not change the existing package manifest or authorize a release. Do not copy credentials, Maven settings, credential stores or machine-specific session folders from another device.

Recent commits, oldest first:

| Commit | Change |
|---|---|
| `51c1b4a` | Spring Boot lead capture API and PostgreSQL event outbox |
| `6a23355` | Owner-notification blueprint and implementation pointers |
| `0d8dd6f` | Local PostgreSQL/n8n/API Compose stack |
| `0a27349` | CI builds, catalogue validation and secret scanning |
| `8f5d64a` | Phase B progress and environment documentation |
| `62647b3` | Classification/draft endpoints, rate limiting, multiple webhook targets |
| `3ff0c89` | Classification and draft n8n blueprints, Code-node tests |
| `0e91deb` | Production Clock/WebConfig bean-cycle fix and regression test |
| `480ffb7` | HTTP smoke-test script |
| `d503873` | Documentation and configuration updates |
| `8f75de4` | Additional documentation |
| `aad7e65` | Angular website template and verified personal-device handoff |

Start with Git CLI, not an IDE's Source Control interface:

```powershell
git clone --branch GH-Copilot_Setup https://samuelvalla-9@github.com/samuelvalla-9/orchbiz.git
Set-Location .\orchbiz
git --no-pager status -sb
git --no-pager log -10 --oneline
git config --local user.name "samuelvalla-9"
git config --local user.email "samuelvictorvalla@gmail.com"
git config --local credential.username "samuelvalla-9"
git config --local core.hooksPath tools/hooks
```

For an existing checkout, inspect changes first; fetch and use a fast-forward-only update when safe. Never discard work to match this handoff. Confirm the current branch before pushing; the example push to `main` in the general Git guide is not the continuation target.

## 3. Read these sources before implementation

| Source | Why it matters |
|---|---|
| `.github\copilot-instructions.md` | Workspace guardrails |
| `docs\agent-workflow.md` | Stages, artifacts, state and approval gates |
| `agents\prompts\` | Canonical specialist contracts |
| `docs\git-workflow.md` | Identity, CLI-only Git and client-data protection |
| `docs\orchbiz-setup-and-trial-run-guide.md` | Trial procedure, capability matrix and roadmap |
| `tools\README.md` | Deterministic CLI and test commands |
| `catalogue\packages\starter-lead-to-appointment.json` | Purchased-package scope and stack |
| `catalogue\README.md` and module manifests | Module versions, contracts and limitations |
| `schemas\` | Required artifact shapes |
| `platform\lead-api\README.md` and source | Runtime API behavior |
| `docker-compose.yml`, `.env.example` | Proposed local stack, not yet runtime-verified |

Repository documentation may lag source. For example, the lead API README's build-command comment still says 18 tests; the last completed suite had 28. Report inconsistencies explicitly rather than assuming every documented feature exists.

## 4. What is implemented and what was verified

### 4.1 Deterministic delivery tooling

The Java CLI is invoked from the repository root using `.\tools\orchbiz.ps1`.

Commands: `init-client`, `parse-intake`, `check-scope`, `check-budget`, `check-gate`, `validate`, `validate-client`, `validate-catalogue`, `scan-secrets`.

It supports CSV and XLSX intake normalization, schema validation, scope checks, cost calculations, gate checks and client isolation checks. Schemas cover state, architecture, website copy, website configuration, workflows configuration, QA reports and packages.

Use the CLI for every check it covers. Exit `0` means pass; `1` means failed/blocked; `2` means usage/environment error. Any non-zero result is a blocker: preserve and quote its `FAIL` lines. Do not substitute hand calculations or agent judgment.

### 4.2 Lead API

Location: `platform\lead-api`. Java 17, Spring Boot 3.3.5, Maven, Flyway.

Implemented:

- Public `POST /api/leads`: tenant lookup, field validation, email/phone normalization, deduplication within a configured window, honeypot handling and storage.
- A `lead.created` outbox with retry attempts and failed-event recording.
- One or more comma-separated webhook targets, authenticated with `X-OrchBiz-Token`.
- Per-IP, in-memory, fixed-window submission rate limiting: default 30 requests/minute; `0` disables it; rejection is `429` with `Retry-After`.
- Admin tenant configuration and lead listing.
- Classification write-back and follow-up draft storage.
- Health endpoint at `/actuator/health`.

Admin calls require `Authorization: Bearer <admin token>`. The value comes from `ORCHBIZ_ADMIN_TOKEN`; no value should be committed or printed in evidence.

| Endpoint | Implemented behavior |
|---|---|
| `PUT /api/admin/tenants/{tenantId}` | Create/update tenant form requirements and dedupe settings |
| `GET /api/admin/tenants/{tenantId}` | Read tenant configuration |
| `GET /api/admin/tenants/{tenantId}/leads?limit=50` | Latest leads, including classification and event-delivery summary |
| `PUT /api/admin/tenants/{tenantId}/leads/{leadId}/classification` | Store validated classification or `pending` |
| `POST /api/admin/tenants/{tenantId}/leads/{leadId}/drafts` | Store a draft, always `awaiting-approval` |
| `GET /api/admin/tenants/{tenantId}/leads/{leadId}/drafts` | List stored drafts |

There is **no implemented approval/send endpoint, staff login, lead-status update endpoint or appointment API implied by this table**.

Classification statuses: `classified`, `needs-review`, `pending`.

Intent labels: `enrolment`, `pricing`, `demo-request`, `schedule-question`, `support`, `other`. Urgency: `high`, `normal`, `low`. Confidence must be within 0-1. Non-pending classifications require a service-interest string.

Migration `V1` creates the initial lead storage; `V2__classification_and_drafts.sql` adds classification fields and `follow_up_drafts`. Preserve applied migrations; add a new migration for later database changes.

### 4.3 Catalogue implementation status

There are 14 module manifests. Eight are `in-development` (four automation modules and four public website modules):

| Module | Implementation | Remaining evidence/limitations |
|---|---|---|
| `capture-and-store-lead` | Lead API | Verified on portable PostgreSQL 16.10 (section 4.5); Docker Compose stack still unverified |
| `notify-business-owner` | n8n blueprint | Email only; dry-run/staging; imported and published in n8n 2.46.4, live execution not yet verified |
| `classify-enquiry` | n8n blueprint plus API write-back | Consent defaults off; imported and published, live execution not yet verified |
| `draft-follow-up` | n8n blueprint plus API draft storage | Template-first; optional LLM; no approval UI or sending; imported and published, live execution not yet verified |
| `business-landing-page`, `services-and-pricing`, `enquiry-form` | Angular website template under `catalogue\websites\business-landing-page\template` | Unit/build, committed mocked-API browser regressions and a local browser run against the real API (section 4.5); real client content and deployment remain |
| `appointment-booking` | External HTTPS booking link or manual follow-up display only | No calendar integration, slot booking, confirmation or reminders |

The remaining six manifests are `0.1.0`, `contract-only`: three website modules and three other automations. Notably, `schedule-appointment` and `send-approved-reminder` are included in the starter package but are not implemented. `weekly-performance-report` is an optional add-on, not an MVP prerequisite.

### 4.4 Last completed validation

The following baseline was rerun on the non-personal workstation for this handoff update. It is not a personal-device check:

| Check | Result on inspected workstation |
|---|---|
| CLI Maven package/tests | Passed: 16 tests, no failures/errors |
| Lead API Maven package/tests | Passed: 28 tests, no failures/errors |
| Catalogue validation | Passed: 14 module manifests and all packages |
| n8n Code-node tests | Passed: CLE-01/02/03 and DFU-01/02/03 |
| Repository secret scan | Passed: 131 files scanned, no secrets found |
| Latest GitHub Actions run on `GH-Copilot_Setup` | Confirmed completed successfully for commit `aad7e65` (run `41678453625`, 2026-11-03) |
| Website dependency install | A clean `npm ci` passed before Playwright/axe dependencies were added; it has not been rerun with the current lockfile |
| Website component tests | Passed: 11 tests, including rendering, required/email validation, payload shape, duplicates, API errors, rate limiting, honeypot forwarding and accessible link names |
| Website production build | Passed with Angular CLI 21.2.11 on Node 24.22.2 LTS |
| Website demo artifacts | Both fictional fixtures passed `site-config` and `website-copy` CLI schema validation |
| Website local preview | Dev server returned HTTP 200 for `/` and `assets/site.config.json`; no backend was running |
| Catalogue after website changes | Passed: all 14 module manifests and packages |
| Secret scan after website changes | Passed: 159 files scanned, no secrets found |

Focused continuation checks on 2026-11-04:

| Check | Result on inspected workstation |
|---|---|
| Website component suite | Passed: 11 tests using available system Node 25.9.0; Angular emitted an unsupported odd-numbered Node warning |
| Website production build | Passed using available system Node 25.9.0; use declared Node 24 LTS for supported-toolchain verification |
| Playwright browser regressions | Passed: 5 tests in installed Microsoft Edge, single worker; fictional fixture and mocked API |
| Playwright bundled Chromium install | Blocked: repeated CDN download failures with `ECONNRESET`; Chromium-specific local run not completed |
| Catalogue validation | Passed: 14 module manifests and all packages |
| GitHub Actions | Latest confirmed run succeeded on `aad7e65`, run `41678453625`, on 2026-11-03; current local CI edits are not part of that run |

These local Maven checks happened to pass with Java 24.0.1 and Maven 4.0.0-rc-4. Those are not the documented Java 17+/Maven 3.9+ baseline; verify the supported toolchain on the personal device rather than treating this as compatibility evidence. Docker was unavailable, so the Compose stack itself was not run; section 4.5 records the portable PostgreSQL, n8n and browser checks that were possible instead.

An earlier system Node 25.9.0 was outside the template's declared Node 24 LTS engine range. To avoid a machine-wide install or changing another project's runtime, Node 24.22.2 LTS was downloaded into the session's private files directory and used explicitly. It reports supported in `ng version`; npm registry downloads succeeded without changing TLS settings. This isolated runtime is temporary session storage, not a substitute for installing Node 24 LTS on the personal device.

An Angular 21 website template now exists at `catalogue\websites\business-landing-page\template`, with an npm lockfile. It renders schema-validated public assets, a configurable landing/services view, validated enquiry form, backend error states and limited external/manual booking content. Its fictional demo config and copy passed the OrchBiz CLI schema checks. The production build and 11 component tests pass, and a local browser run against the real API passed (section 4.5). A committed single-worker Playwright suite now covers the fictional fixture with a mocked API; it does not replace live API/CORS/database verification. Do not treat the template as release-ready until real client content and deployment checks are complete.

Earlier historical results and limitations:

| Check | Last result |
|---|---|
| HTTP smoke script | Previously passed against a running API with H2 and a local fake webhook; rerun against PostgreSQL in section 4.5 |
| Smoke negative paths | Previously: missing token/unreachable API returned exit 2; wrong token returned exit 1 |
| Live rate-limit exercise | Previously: excess submissions returned 429 |
| Docker/PostgreSQL runtime | Docker unavailable; PostgreSQL verified with portable binaries (section 4.5) |
| Real n8n import and executions | Import, credential binding and publishing verified (section 4.5); executions not exercised |
| Website build/tests | Unit/build passed; browser-to-API, CORS and axe audit verified in section 4.5 |

The smoke run found a real startup dependency cycle hidden by the test Clock override. `ClockConfig` now owns the production Clock bean, and `ProductionWiringTest` covers startup without that override. Keep this regression coverage.

### 4.5 Portable integration run on the non-personal workstation (no Docker, winget or tunnels)

Docker and WSL are not installed and winget is blocked by policy, so the stack was assembled from portable runtimes in the session's private folder only, with no machine-wide install or configuration change. Port 8080 was already used by an unrelated process, so the API ran on `18080`. All data was fictional; nothing was sent externally.

| Runtime | How it was run |
|---|---|
| PostgreSQL 16.10 (Compose uses `postgres:16`) | EDB portable binaries zip; `initdb` with scram-sha-256 on `127.0.0.1:55432`; `orchbiz` and `n8n` databases |
| Lead API | `java -jar platform\lead-api\target\lead-api.jar` with Compose-equivalent environment variables |
| n8n 2.46.4 | `npm install n8n@2.46.4 --force --ignore-scripts` on Node 24.22.2, PostgreSQL backend, `127.0.0.1:5678` |
| Browser | Installed Chrome driven headless by `playwright-core` plus `axe-core` (session-only script, not committed) |

| Check | Result |
|---|---|
| Flyway on real PostgreSQL | V1 and V2 applied cleanly |
| `lead-api-smoke.ps1 -SkipNotification` | Passed: HEALTH, AUTH, TENANT, CSL-01..04, SPAM, CLE-API, DFU-API |
| Browser run of the Angular template against the API via the dev proxy (fictional `example-learning-studio` tenant) | 18/18 passed: render, services, title, booking manual fallback, required/email validation without POST, submission stored in PostgreSQL, reset, duplicate response, no console errors, axe WCAG 2.1 A/AA with 0 violations, honeypot accepted but not stored, cross-origin submission from an allowed origin, preflight denied for an unlisted origin, friendly tenant error (403), no horizontal overflow at 390px |
| n8n credentials and workflow import | 4 `*_REF` credentials and all 3 blueprints imported; each node bound to the intended credential after the fix below |
| n8n publish/restart | All 3 workflows published; after a restart n8n reported 3 published workflows and `/healthz` returned 200 |
| Committed browser regressions | 5 single-worker Playwright tests cover mobile rendering, validation, success/error responses and axe WCAG 2.1 A/AA using the fictional fixture and mocked API |
| CLI after changes | `mvn package` passed 16 tests; `validate-catalogue` passed |
| Not run | Smoke with notification (NBO-01) and live NBO/CLE/DFU executions were stopped at the operator's request; they remain for the personal device |

Defects found by this run and fixed in the repository:

- **n8n credential binding:** the blueprints referenced credentials by name without an `id` key. On import, n8n caches by an undefined id for each credential type, so every Header Auth slot in classification and drafting was bound to the webhook credential and the admin-API and LLM calls would have sent the wrong token. Every reference now declares `"id": null`, and `validate-catalogue` rejects a credential reference without an explicit `id` key (covered by `OrchbizCliTest`).
- **Honeypot never sent:** the form always submitted an empty `_hp`. It now reads the hidden input and clears it on reset.
- **Accessibility:** a service link's `aria-label` did not contain its visible text (axe `label-content-name-mismatch`). It is now `Ask about this service: <name>`.
- Two Angular unit tests were added (11 total, all passing).

Notes for the personal device: Docker Compose remains the supported path. On Windows, an npm-installed n8n needs `--force --ignore-scripts` (a Linux-only optional dependency and the Kafka native module otherwise fail); the Kafka node is not used by OrchBiz, and n8n warns that running outside a container is deprecated. Under heavy machine load, Vitest worker start-up and n8n start-up timed out or took several minutes; this was environmental rather than code failure.

## 5. Personal-device bootstrap and baseline

On the personal device, inspect Git, Java 17+, Maven 3.9+, Node/npm, Docker with Compose, and optionally GitHub CLI. The versions observed on the non-personal workstation above do not describe the personal device. Select a Node version supported by the Angular CLI before scaffolding.

```powershell
git --version
java -version
mvn -version
node --version
npm --version
docker version
docker compose version
```

Do not transfer corporate proxy configuration or disable TLS verification. The npm certificate problem may not exist on the personal network. Investigate actual failures there instead of applying corporate workarounds blindly.

For the website template, use Node 24 LTS (`.nvmrc`) and run its locked install, tests and build:

```powershell
Set-Location .\catalogue\websites\business-landing-page\template
npm ci
npm test -- --watch=false
npm run build
```

For a local API-backed preview, `npm start` serves `http://localhost:4200` and proxies `/api` to `http://localhost:8080`. Register and activate the configured tenant first; the demo `example-learning-studio` tenant is not created automatically. The proxy does not start the API or PostgreSQL.

Run the baseline before changing implementation:

```powershell
mvn -B -f tools\orchbiz-cli\pom.xml package
mvn -B -f platform\lead-api\pom.xml package
.\tools\orchbiz.ps1 validate-catalogue
node .\tools\workflow-tests\code-nodes.test.js
.\tools\orchbiz.ps1 scan-secrets .
```

Run commands individually and inspect exit codes before continuing. In Windows PowerShell 5.1, do not use PowerShell 7-only operators such as `&&`.

If GitHub CLI is available, authenticate with the personal account and check the branch's Actions runs. An authorization failure/404 is not evidence that CI passed or that no workflow ran.

## 6. Priority 1: Verify the real local stack

### 6.1 Prepare local configuration

1. Copy `.env.example` to `.env` only if `.env` does not already exist.
2. Replace all placeholder secret values locally with independently generated, strong random values.
3. Keep the n8n encryption key stable for that environment; replacing it can make saved credentials unreadable.
4. Confirm `.env` is ignored with `git check-ignore .env`.
5. Keep all service ports bound to loopback as in the Compose file.

The stack is local/staging only. No paid provisioning, external messages, DNS changes or production deployment is authorized by this handoff.

```powershell
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail 100 lead-api
docker compose logs --tail 100 n8n
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected: PostgreSQL healthy, API health `UP`, n8n accessible at `http://localhost:5678`.

The Compose file currently uses `n8nio/n8n:latest`. Record the actual version tested and pin a verified version as part of making the stack reproducible. Check actual node compatibility on that version rather than assuming the handwritten workflows import unchanged.

The database initialization script creates n8n's database on first initialization of an empty PostgreSQL volume. Existing volumes may need an explicit, reviewed database setup step. Do not solve initialization issues by deleting volumes without the owner's approval.

### 6.2 Import notification in dry-run mode first

1. Complete local n8n owner-account setup.
2. Create Header Auth credential `LEAD_EVENTS_WEBHOOK_HEADER_REF`, with header name `X-OrchBiz-Token` and the local webhook-token value.
3. Import `catalogue\automations\notify-business-owner\notify-business-owner.workflow.json`.
4. Resolve credential bindings, inspect the Code node, and retain `mode: 'dry-run'`. The blueprints declare `"id": null` on every credential reference so n8n binds each one by name; confirm in the editor that each node shows the intended `*_REF` credential.
5. Resolve any SMTP credential validation needed by the imported node without sending a message. Do not activate staging email without explicit permission.
6. Activate the workflow so its production `/webhook/` URL exists.
7. Leave the API webhook list pointing only at this notification workflow initially.

Read the admin token from local secret storage into `ORCHBIZ_ADMIN_TOKEN` without printing it. Compose's `.env` does not automatically populate the current PowerShell process environment.

```powershell
.\tools\smoke\lead-api-smoke.ps1 -BaseUrl http://localhost:8080
```

Expected: all checks pass, a dry-run notification execution appears in n8n, and the lead/draft records persist in PostgreSQL. Restart the services and verify persistence.

The smoke script creates records in `smoke-test-tenant`. It tests API behavior and HTTP acceptance by webhooks; **it does not prove that downstream notification/classification work completed**. Inspect n8n executions separately. `-SkipNotification` is available for API-only troubleshooting, but a skipped check is not integration evidence.

Do not run the script against production. Avoid repeated rapid runs that exhaust the default rate-limit window.

## 7. Priority 2: Exercise and harden classification/drafting

Import:

- `catalogue\automations\classify-enquiry\classify-enquiry.workflow.json`
- `catalogue\automations\draft-follow-up\draft-follow-up.workflow.json`

Create `ORCHBIZ_ADMIN_API_REF` as Header Auth with header `Authorization` and the full local Bearer value. It is a powerful operator credential, not tenant-scoped authentication.

Create `LLM_API_REF` only when an approved test provider/credential is available and the owner explicitly permits its use. The current HTTP nodes implement an OpenAI-compatible chat-completions shape; broader provider names in manifests do not prove native Anthropic/Azure compatibility.

Review the `CONFIG` constants in the Prepare nodes after import:

| Setting | Required handling |
|---|---|
| `aiDataConsent` | Defaults false; never infer or manufacture consent |
| `serviceNames` | Use approved service names; not invented commercial claims |
| `leadApiBaseUrl` | `http://lead-api:8080` from the n8n container |
| `draftWebhookUrl` | Verify routing from inside n8n; default is its localhost port 5678 |
| Templates/business name | Replace examples with approved fictional test content or authorized client content |
| `autoSend` | Must remain false |
| `useLlm` | Defaults false; template drafting does not require LLM access |

Keep modules without paid credentials testable using synthetic fixtures or a local compatible fake provider. Do not enable a paid integration just to make a demonstration pass.

After both production webhooks exist, set the local webhook list to notification plus classification:

```text
http://n8n:5678/webhook/orchbiz/lead-events,http://n8n:5678/webhook/orchbiz/classify-enquiry
```

Recreate the API service with `docker compose up -d lead-api` after changing its environment. A nonexistent/inactive target returns 404 and causes the event to retry for all targets.

Test and capture actual n8n execution evidence:

| Scenario | Expected behavior |
|---|---|
| No AI consent | No provider request; classification stored as pending |
| Valid demo enquiry | Allowed intent/service labels stored |
| Invalid labels or low confidence | Review state, bounded labels and visible review reason |
| Provider timeout/malformed result | Lead survives; classification pending |
| Template-only draft | Stored awaiting approval; no message sent |
| Forbidden LLM claim | Approved plain-template fallback |
| `autoSend: true` | Explicit configuration failure |
| API write-back failure | Visible failed execution, not assumed completion |
| Duplicate/retried event | Verify effects are not silently lost or duplicated |

### Reliability limitations that must not be hidden

- Webhooks use `responseMode: onReceived`: API `SENT` currently means all webhook endpoints accepted the HTTP request, not that workflows succeeded. The lead list field `notificationStatus` is an aggregate event-dispatch summary, not proof of an email delivery.
- Workflow deduplication records event IDs in Prepare nodes before downstream work completes. Static data persists only under n8n's execution rules, is bounded to 500 IDs, and is not durable API-level idempotency. Test both successful duplicates and failure/retry cases.
- API retries resend to every target, including ones that accepted earlier attempts. Workflow failures after early HTTP acknowledgment may not trigger API retries at all.
- Classification's draft-trigger HTTP node continues on error. Test failure visibility and recovery; a successful classification is not proof that a draft exists.
- There is no durable idempotency key enforced for draft creation. Replays can create more than one draft.
- Review reasons are workflow execution data; the current classification request has no dedicated reason field.
- Code-node tests mock n8n globals and do not establish credential binding, expression resolution, item linking or actual HTTP-node behavior.
- Workflow configuration is static, not automatically generated per engagement. Do not route multiple clients through one client's business name, service list, consent or templates.

These are continuation tasks to investigate and address with targeted tests, not claims that failures have already been reproduced. Prefer explicit failure states and recoverable/idempotent processing over silently ignoring errors. Record remaining limitations honestly.

## 8. Priority 3: Configure and harden the reusable Angular website

The public website modules now have an Angular implementation in `catalogue\websites\business-landing-page\template`. The included fixture is clearly fictional; replace it only with validated, approved artifacts when configuring a real preview.

Start with the included public modules: `business-landing-page`, `services-and-pricing`, `enquiry-form`, and the approved `appointment-booking` behavior. Treat the authenticated `lead-dashboard` separately.

Remaining configuration, integration and verification requirements:

- Render from schema-valid `site.config.json` and `website-copy.json`, not hardcoded client data.
- Respect `enabledModules`, exact manifest versions, theme tokens, content sources and client identity.
- Resolve the delivery-time content path to build/runtime assets explicitly; do not assume `..\content\website-copy.json` is directly browser-accessible.
- Use the actual enquiry payload contract: `{tenantId, submittedAt, sourcePage, fields}`.
- Handle success, duplicate, validation errors, unavailable API, inactive tenant and rate limiting clearly.
- Match CORS to the preview origin; browser behavior must be tested, not inferred from HTTP smoke results.
- Keep secrets out of public configuration, JavaScript bundles and browser storage.
- Never fabricate testimonials, prices, accreditation, awards or performance claims.
- A single-worker Playwright suite now runs in CI against the fictional fixture, checking form validation, mocked API success/errors, mobile overflow and axe WCAG 2.1 A/AA. It does not replace the live API/CORS/PostgreSQL integration checks recorded in section 4.5.
- Use a supported Angular/Node combination for client configuration and continue running the committed CI build/unit/browser checks.

The site field-name schema now accepts underscore and hyphen keys used by the API (including `full_name` and `course_interest`). Keep the schema, manifests, tenant requirements, frontend and workflows aligned; do not add one-off renames.

The site schema also describes field validation as client/server applied, but tenant configuration does not currently store arbitrary per-field regex/length rules. Verify which rules the backend actually enforces and close or document the gap; frontend-only validation is not equivalent.

Use synthetic demo fixtures clearly labelled as fictional. Never use another engagement's input as live client data. Update manifests and implementation pointers only for code that actually exists; do not mark a module `tested` merely because its schema validates.

## 9. Priority 4: Complete the starter package honestly

After the public lead-capture demo works, agree a bounded next milestone with the owner:

| Gap | Required development |
|---|---|
| Staff authentication/dashboard | Tenant-bound identity/authorization, staff allow-list, dashboard UI and API access |
| Follow-up approval | Auditable approval state and permission checks before any sending |
| Appointment scheduling | Implement the manifest's supported booking mode and conflict/error behavior |
| Approved reminders | Explicit approved-message delivery path, credentials, retry/idempotency and audit records |
| Client preview command | Repeatable config validation, build and preview without exposing client files |
| Deployment operations | Staging runbook, backups/restore, monitoring, retention and rollback |

Do not put the global admin token in an Angular dashboard to simulate authentication. Admin endpoints are tenant-filtered by path, but the shared admin token can intentionally operate on all tenants. That is not staff tenant isolation and does not satisfy the dashboard's cross-tenant authorization test.

Other known limitations include a possible concurrent-submission dedupe race, single-instance event dispatch and per-instance in-memory rate limiting. Production work must assess these constraints, configure a trusted proxy correctly, and avoid trusting arbitrary forwarded IP headers.

Do not advertise a fully working Lead-to-Appointment package while appointments/reminders remain contracts. A narrower demo can be useful, but label its scope explicitly. Optional FAQ, social content and weekly-report modules should not distract from the included package.

## 10. Client workflow and non-negotiable gates

Platform development can use isolated synthetic fixtures. A real client engagement still follows the stage workflow:

1. Initialize `clients\<slug>` with `.\tools\orchbiz.ps1 init-client <slug>`.
2. Place the authorized questionnaire in its intake directory and run `parse-intake`.
3. Generate requirements and architecture through the specialist contracts; run scope/budget checks.
4. Stop for the owner's Gate 1 approval, including approver and timestamp.
5. Run `check-gate <slug> gate1` successfully before Stage 3 brand/site/workflow work.
6. Generate and validate artifacts; run QA using actual evidence.
7. Stop for the owner's Gate 2 release approval.
8. Run `check-gate <slug> gate2` successfully before Stage 5 handover/deployment preparation.

Maintain stage timestamps, state and audit log, and validate state against its schema. Agents must never set either gate approval on the owner's behalf.

No client engagement files may be committed; use `clients\_template` only for reusable templates. Keep runtime credentials, `.env`, PII, exports and real execution logs out of Git. Use credential references in generated artifacts.

No live external messages, paid credential provisioning, production deployment or DNS changes without explicit approval and operator execution.

## 11. Evidence, commits and handback

Use the smallest relevant tests during implementation, then the baseline checks before a handback. Add real PostgreSQL, n8n and browser integration coverage as those runtimes become available.

Keep client QA evidence under `clients\<slug>\qa\evidence\` and do not commit it. Generic reusable tests and sanitized fictional fixtures belong with the relevant component/tooling. Never publish resolved Compose secrets or full credential-bearing logs as evidence.

When instructed to commit/push:

- Use Git CLI and the repository identity from `docs\git-workflow.md`.
- Stage specific intended paths; avoid indiscriminate staging of client data.
- Run `.\tools\orchbiz.ps1 scan-secrets --staged`; do not bypass the hook.
- Include `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>` in agent-authored commits.
- Use logical commits for runtime fixes, website implementation, tests and docs.
- Push the intended branch, not `main` by default; do not force-push.
- Prefer normal Git push. The corporate proxy-specific `git zpush` helper is not a personal-device prerequisite.

The next agent's handback must distinguish:

1. Implemented behavior.
2. Checks actually run and their outcomes.
3. Manual integration evidence and versions tested.
4. Untested behavior, blockers and deferred scope.
5. Whether changes are local, committed or pushed.

## 12. Suggested first instruction to the personal-device agent

> Read `docs\personal-device-agent-handoff.md` and the canonical documents it references. Inspect this checkout and preserve existing changes. Reproduce the CLI/API/Code-node baseline and run the Angular template's `npm ci`, unit tests, Playwright browser suite and production build with Node 24 LTS; the committed browser suite uses a mocked API and does not verify CORS or persistence. Then, on a Docker-capable machine, bring up PostgreSQL/n8n/API, import notification in dry-run mode, run the smoke test (with notification) against PostgreSQL and record actual NBO/CLE/DFU workflow executions; section 4.5 already covers PostgreSQL migrations, API smoke checks, n8n import/publishing and a browser run without Docker. Keep credentials local, do not enable paid/live messaging or production deployment, and never approve human gates for me. Report verified outcomes and remaining gaps rather than claiming the entire package is complete.
