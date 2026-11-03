# Manual test runbook (any developer device)

Run these checks yourself in a terminal, outside the agent conversation. This
avoids agent polling and large log output consuming AI tokens. Tests still use
CPU, memory, disk, network and time; running them manually does not remove that
machine cost. Heavy runs on the previous workstation took about 15 minutes.
Times vary by hardware, dependency caches and other running programs.

This guide covers the current repository checks, local integration and manual
acceptance scenarios. It does not make contract-only modules testable or certify
production readiness. Historical results are in
[the handoff, section 4.5](personal-device-agent-handoff.md#45-portable-integration-run-on-the-non-personal-workstation-no-docker-winget-or-tunnels).

## 1. Prepare and record the device

Use Windows PowerShell from the repository root. Replace the path below with
your checkout. On another OS use its shell/path equivalents for portable checks;
the smoke script requires Windows PowerShell. No winget or tunnel is required. Install missing tools only
with the device owner's permission, using official installers.

### Prerequisites: install before starting

| Tool or resource | Required for | Installation/readiness |
|---|---|---|
| Git CLI and repository checkout | All steps, version tracking and final hygiene | Install [Git](https://git-scm.com/downloads); obtain the complete checkout, including the website template, lockfile and this guide. Open the repository root, not just the website folder. |
| Windows PowerShell 5.1 | Windows commands and HTTP smoke script | Included with supported Windows installations. The documented smoke script uses Windows/.NET APIs; on a non-Windows host use a Windows test environment for that script rather than assuming PowerShell Core compatibility. An IDE or agent extension is not required. |
| Java **JDK 17**, not just a JRE | CLI and API builds/tests, CLI checks and commit hook | Install [Temurin 17](https://adoptium.net/temurin/releases/?version=17); set `JAVA_HOME` to its JDK folder and make `java` and `javac` available on `PATH`. |
| Maven **3.9+** | Java dependency restore, tests and packaging | Install [Apache Maven](https://maven.apache.org/install.html); add its `bin` folder to `PATH`. `mvn -version` must report the intended JDK. Maven is not bundled as a wrapper in these commands. |
| Node.js **24 LTS** and npm **11** | Workflow Code-node checks and Angular tests/build/preview | Install [Node.js](https://nodejs.org/en/download) 24 LTS with npm. `node --version` must report `v24.*` and `npm --version` must satisfy the website's `engines` requirement. A version manager is optional. |
| Docker engine and Compose v2 | Container-based PostgreSQL, n8n and API integration (steps 5-9) | Use [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/) on a supported Windows device, or [Docker Engine](https://docs.docker.com/engine/install/) plus the [Compose plugin](https://docs.docker.com/compose/install/) on a supported Linux host. Start the engine; both client and server must appear in `docker version`. Use Linux containers for this stack. Review licensing on a work device. |
| Supported virtualization/container backend | Docker on Windows | Follow Docker's current OS/hardware requirements. Enable virtualization and the supported WSL 2 or Hyper-V backend with owner/admin permission; a reboot may be needed. WSL is not needed for the non-container checks. If policy blocks this setup, mark container checks blocked rather than bypassing policy. |
| Current Chrome, Edge or Firefox with DevTools | Manual website, responsive and CORS checks | Install/update through the browser's official channel. No ChromeDriver or global Playwright install is needed for the manual checklist. |
| Approved axe browser extension (optional) | The browser accessibility audit | Install [axe DevTools](https://www.deque.com/axe/devtools/) if permitted. Without it, record the automated accessibility audit as **NOT RUN**; keyboard testing alone does not replace it. |
| Network access and trusted TLS configuration | First dependency restore and image downloads | Allow authorized access to Maven repositories, `registry.npmjs.org` and the container registries used by Compose. Configure any approved proxy/CA correctly; never disable TLS checks. Offline runs require already cached dependencies/images. |
| Available memory, disk and local ports | Builds, dependency caches and running services | Ensure space for Maven/npm caches, `node_modules`, build output, container images and database volumes (several GB, varying by environment). Docker must have resources meeting its current requirements. Free ports 4200, 5432, 5678 and 8080, or document deliberate local alternatives. |
| Local secrets and fictional test data | API/n8n integration | Prepare private `.env` values and a password manager before step 5. Create the local n8n owner account and credential references in steps 7-8. Never reuse production secrets or another client's data. |

**You do not need to install these globally:** Angular CLI, TypeScript, Vitest,
PostgreSQL, n8n, or a standalone `psql` client. `npm ci` installs the website's
locked local tools; `npm` scripts use them automatically. Compose supplies
PostgreSQL, n8n, the API runtime and the database client. The Code-node script
uses only Node built-ins. Do not run `npm install -g @angular/cli` or install
native database services just to follow this guide.

Playwright is not currently a committed test dependency or suite; manual browser
checks do not require it. A local SMTP sink is optional only if the dry-run
workflow editor requires SMTP configuration. Live SMTP, AI API keys, tunnels,
paid hosting and production deployment are **not prerequisites** for these
local tests. Provider-failure cases require a separately approved local fake
provider; otherwise record them as **NOT RUN**.

After installation, reopen the terminal so `PATH` changes take effect, then
verify the tools:

```powershell
Set-Location C:\path\to\orchbiz
git status --short
git rev-parse HEAD
java -version
javac -version
mvn -version
node --version
npm --version
docker version
docker compose version
```

For Windows shell readiness, inspect `$PSVersionTable.PSVersion`. Confirm the
running Docker engine is using Linux containers:

```powershell
docker info --format '{{.OSType}}'
```

Expect `linux`. If an executable is missing or the Docker
server is unreachable, resolve that prerequisite before its dependent tests.
GitHub CLI is optional for testing; Git authentication and the repository-local
identity in [the Git workflow](git-workflow.md) are needed only to commit/push.

Use Java 17, Maven 3.9+ and Node 24 LTS (npm 11). Docker with Compose is needed
for steps 5-9 using the stack below, not for steps 2-4. System Node 25 is not
the supported website baseline. If Docker is unavailable, complete steps 2-4
and 10; mark integration checks **BLOCKED**, not passed. The previous session's
portable runtimes, credentials and browser script are not supplied with a clone.

Close unnecessary heavy applications. Run one suite at a time; don't run Maven,
npm installs and containers concurrently on a memory-constrained device.
Preserve existing uncommitted changes. Never disable TLS verification or change
corporate policy to make an install pass.

Keep a local record outside the repository: date, commit plus dirty status,
versions, command, exit code, result and a short failure excerpt. For authorized
client QA, use that client's `qa\evidence\` folder, never commit it. Do not copy
secrets, full environment dumps or customer data into agent messages.

**After every native command**, immediately enter `$LASTEXITCODE` to inspect
its result. Continue only on `0`. OrchBiz CLI/smoke exit codes are `0` pass,
`1` failed/blocked, `2` usage/environment error. Stop on nonzero, save `FAIL`
lines and fix the cause before dependent steps. For PowerShell HTTP commands,
a thrown error or unexpected status/body is a failure, even if a stale
`$LASTEXITCODE` happens to be zero. Do not run dependent lines blindly.

## 2. Build and test the Java projects

Run each command separately from the root and inspect its result:

```powershell
mvn -B -f tools\orchbiz-cli\pom.xml package
$LASTEXITCODE

mvn -B -f platform\lead-api\pom.xml package
$LASTEXITCODE
```

Expected: `BUILD SUCCESS`, zero failures/errors, and jars in each project's
`target` directory. The last verified counts were 16 CLI and 28 API tests;
counts may grow. Reports are in each project's `target\surefire-reports`.
API unit tests use H2, not real PostgreSQL.

The CLI tests copy workspace fixtures into temporary folders and can be costly.
An apparently quiet Maven run is not proof of a hang. Check the terminal/process
and reports before stopping it; do not launch another copy. To investigate just
one failing test instead of repeating everything:

```powershell
mvn -B -f tools\orchbiz-cli\pom.xml "-Dtest=OrchbizCliTest#catalogueChecksImplementationPointersAndBlueprints" test
```

This targeted run is not a substitute for recording a full suite result.
Do not use `-DskipTests` as passing evidence.

## 3. Validate catalogue and workflow logic

```powershell
.\tools\orchbiz.ps1 validate-catalogue
$LASTEXITCODE

node .\tools\workflow-tests\code-nodes.test.js
$LASTEXITCODE
```

Expected: all 14 module manifests and packages valid; CLE-01/02/03 and
DFU-01/02/03 pass. Catalogue validation checks structure and implementation
pointers, not live integrations. Code-node tests mock n8n globals.
`validate-catalogue` is normally smaller than the full Maven suite; however,
the wrapper can build the CLI if its jar is missing or stale. Avoid redundant
builds, repeated full catalogue runs and unnecessary dependency reinstalls.

## 4. Website install, tests, build and fixture validation

```powershell
Push-Location .\catalogue\websites\business-landing-page\template
npm ci
$LASTEXITCODE
npm test -- --watch=false
$LASTEXITCODE
npm run build
$LASTEXITCODE
Pop-Location
```

Stop within that directory if any command fails; don't continue automatically.
Use `npm ci` for a fresh checkout or changed lockfile, not before every test.
Expected: component tests pass (last verified: 11), production build succeeds,
and static output appears under `dist\orchbiz-website-template\`.
Worker-start timeouts under memory pressure need investigation before being
classified as code failures.

From the repository root:

```powershell
.\tools\orchbiz.ps1 validate site-config .\catalogue\websites\business-landing-page\template\public\assets\site.config.json
$LASTEXITCODE
.\tools\orchbiz.ps1 validate website-copy .\catalogue\websites\business-landing-page\template\public\assets\website-copy.json
$LASTEXITCODE
```

Both must pass. These are fictional public fixtures, not a customer's intake.

## 5. Start the local integration stack

This creates local data and pulls/builds container images. Use no production
endpoint, live SMTP account or paid AI credential.

1. Copy `.env.example` to `.env` only if `.env` does not already exist:

   ```powershell
   if (-not (Test-Path .env)) { Copy-Item .env.example .env }
   ```

2. Edit `.env` locally. Replace every `change-me-*` value with a distinct strong
   secret generated in a password manager. Preserve the n8n encryption key for
   this environment. Keep `ORCHBIZ_ALLOWED_ORIGINS=http://localhost:4200`.
   Initially leave the webhook list targeting notification only.
3. Check that ports 5432, 5678 and 8080 are free. Do not kill another project's
   process. If there is a conflict, deliberately change the host port mapping
   in a local Compose override and use that port in subsequent URLs and the
   website proxy (`proxy.conf.json`); the API's container port remains 8080.
4. Start and inspect the stack:

   ```powershell
   docker compose up -d --build
   docker compose ps
   docker compose logs --tail 100 lead-api
   docker compose logs --tail 100 n8n
   Invoke-RestMethod http://localhost:8080/actuator/health
   ```

Expected: PostgreSQL healthy, API `UP`, Flyway migrations applied, n8n editor
accessible at `http://localhost:5678`. Record the actual n8n version; Compose
currently uses `latest`, not a pinned version. Portable n8n 2.46.4 import evidence
does not prove a different container version works.

If a reused volume lacks the `n8n` database, follow the reviewed database setup
in the handoff; do not delete the volume to bypass the problem.

## 6. API-only smoke test

Read the admin token without putting it into shell history:

```powershell
$secureToken = Read-Host 'Local ORCHBIZ_ADMIN_TOKEN from .env' -AsSecureString
$env:ORCHBIZ_ADMIN_TOKEN = [System.Net.NetworkCredential]::new('', $secureToken).Password
.\tools\smoke\lead-api-smoke.ps1 -BaseUrl http://localhost:8080 -SkipNotification
$LASTEXITCODE
```

Expected: health, auth, tenant isolation, CSL-01..04, spam, classification
write-back and draft API checks pass against PostgreSQL. Notification is
explicitly **SKIPPED**. The script creates fictional `smoke-test-tenant` data;
avoid repeated rapid runs that hit the rate limiter.

For smoke-script failure handling, use a separate terminal: try an unset admin
token (expected exit `2`), a deliberately wrong token against the running API
(expected exit `1`), and the correct token against a known unused local port
(expected exit `2`). These are expected negative results, not successful
integration runs. Restore the correct token/base URL before continuing.

## 7. Notification workflow (local dry-run)

1. Complete local n8n owner setup. Create Header Auth credential
   `LEAD_EVENTS_WEBHOOK_HEADER_REF`: header `X-OrchBiz-Token`, value equal to
   `LEAD_EVENTS_WEBHOOK_TOKEN` in `.env`.
2. Import `catalogue\automations\notify-business-owner\notify-business-owner.workflow.json`.
   Confirm `mode: 'dry-run'` in the Prepare node. Resolve SMTP validation using
   only a local test sink if necessary, never live credentials.
3. Check node bindings. Blueprint references must retain `"id": null` and their
   `*_REF` names; missing `id` previously caused incorrect credential binding.
4. Publish/activate the local workflow so its production `/webhook/` URL exists.
   Do not substitute the temporary `/webhook-test/` URL.
5. Run the smoke test without the skip flag:

   ```powershell
   .\tools\smoke\lead-api-smoke.ps1 -BaseUrl http://localhost:8080 -NotificationTimeoutSeconds 120
   $LASTEXITCODE
   ```

6. Require NBO-01 to pass **and** inspect the corresponding successful n8n
   execution: valid event, dry-run branch reached, email branch not executed.
   Save the execution ID and result, with secrets removed.

`notificationStatus=SENT` proves HTTP acceptance, not workflow completion or
email delivery. If no execution exists, check webhook activation, URL, token,
API logs and outbox status before retrying.

## 8. Classification and draft workflows (no external AI or sending)

Import `classify-enquiry` and `draft-follow-up` from their catalogue folders.
Create `ORCHBIZ_ADMIN_API_REF` as Header Auth: header `Authorization`, value
formed from `Bearer`, one space, and the local admin token. This credential is
powerful; never put it in website assets.

In the imported workflow copies, verify:

| Setting | Local test value |
|---|---|
| `leadApiBaseUrl` | `http://lead-api:8080` inside Compose |
| `draftWebhookUrl` | Reachable draft webhook, normally `http://localhost:5678/webhook/orchbiz/draft-follow-up` inside the same n8n container |
| `aiDataConsent` | `false` |
| `useLlm` | `false` |
| `autoSend` | `false` |
| Business, services, templates | Fictional test values only |

Confirm webhook, admin and optional LLM slots are not accidentally bound to
the same Header Auth credential. Do not provide a paid key just to pass node
validation. If the editor requires a credential on an unused branch, use a
local-only test reference and ensure the branch never executes.

Publish both workflows, add the classification URL to `.env`:

```text
LEAD_EVENTS_WEBHOOK_URL=http://n8n:5678/webhook/orchbiz/lead-events,http://n8n:5678/webhook/orchbiz/classify-enquiry
```

Apply the environment change with `docker compose up -d lead-api`.
Run a fresh smoke test and inspect classification for its newly created lead:
expect `pending`, no AI HTTP request and a successful admin write-back execution.
Use the admin lead-list endpoint to get the real lead ID:

```powershell
$adminHeaders = @{ Authorization = 'Bearer ' + $env:ORCHBIZ_ADMIN_TOKEN }
$leadsUrl = 'http://localhost:8080/api/admin/tenants/smoke-test-tenant/leads?limit=50'
Invoke-RestMethod $leadsUrl -Headers $adminHeaders
```

No-consent classification need not trigger drafting. Test drafting separately
with a synthetic `lead.classified` event referencing an existing smoke lead:

```powershell
$leadId = Read-Host 'Existing smoke-test-tenant lead ID'
$secureWebhook = Read-Host 'Local LEAD_EVENTS_WEBHOOK_TOKEN' -AsSecureString
$hookHeaders = @{
    'X-OrchBiz-Token' = [System.Net.NetworkCredential]::new('', $secureWebhook).Password
}
$draftEvent = @{
    event = 'lead.classified'
    eventId = [guid]::NewGuid().ToString()
    tenantId = 'smoke-test-tenant'
    leadId = $leadId
    fields = @{ full_name = 'Fictional Developer'; course_interest = 'Example Course' }
    classification = @{
        status = 'classified'; intent = 'pricing'; urgency = 'normal'
        serviceInterest = 'Example Course'; confidence = 1.0
    }
} | ConvertTo-Json -Depth 6
Invoke-RestMethod -Method Post -Uri http://localhost:5678/webhook/orchbiz/draft-follow-up -Headers $hookHeaders -ContentType 'application/json' -Body $draftEvent
Invoke-RestMethod "http://localhost:8080/api/admin/tenants/smoke-test-tenant/leads/$leadId/drafts" -Headers $adminHeaders
```

Wait for the execution to finish before the final GET if necessary. Require a
successful draft execution and a stored `source: template`,
`status: awaiting-approval` draft. No message or AI request should be sent.
Repeat the exact event to check duplicate behavior; record any duplicate draft
or lost effect as a failure. A new scenario needs a new event ID.

Also test a webhook request with missing/incorrect auth: it must be rejected,
not start processing. Inspect failed executions and recovery after a deliberately
invalid local write-back URL in a separate test workflow. Restore the URL after
the test. Early webhook acknowledgment, bounded deduplication and draft-trigger
error handling have known limitations; see handoff section 7.

Provider timeout, malformed output, low confidence and forbidden-claim scenarios
have mocked Code-node coverage only until exercised with an approved local fake
provider. Mark those live cases **NOT RUN** if you have no such fixture. Do not
turn on AI consent, paid calls or real messages to complete this checklist.

## 9. Website/browser and persistence checks

Register the fictional tenant using the API, not another client's intake:

```powershell
$tenant = @{
    displayName = 'Example Learning Studio (fictional)'
    requiredFields = @('full_name', 'email', 'phone', 'course_interest')
    emailField = 'email'; phoneField = 'phone'; active = $true
} | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri http://localhost:8080/api/admin/tenants/example-learning-studio -Headers $adminHeaders -ContentType 'application/json' -Body $tenant
```

In a second terminal, start the website and leave it running:

```powershell
Set-Location C:\path\to\orchbiz\catalogue\websites\business-landing-page\template
npm start
```

Open `http://localhost:4200`; use browser DevTools Network and Console. Check:

| Action | Required result |
|---|---|
| Load page | Hero, services, title and manual booking fallback render; no console errors |
| Submit empty form / invalid email | Labelled errors, no lead POST |
| Submit unique fictional email/phone | HTTP 201, success state, lead present in admin list |
| Resubmit same details | HTTP 200 duplicate, no second stored lead |
| Reset | Visible inputs and honeypot clear |
| Set hidden `#website-url` value in DevTools, then submit valid fields | HTTP 202; no new stored lead |
| Temporarily deactivate the fictional tenant through its admin config | Friendly 403 error; restore `active: true` afterward |
| Temporarily stop local API | Visible failure, not success; restart it afterward |
| Keyboard-only form use | Focus visible; labels and errors understandable |
| Responsive mode at 390px | No horizontal overflow; form and navigation usable |
| Local axe browser audit, if installed/approved | Record WCAG 2.1 A/AA violations, not just a score |

Unique details are important for honeypot/storage checks; a previous duplicate
must not be mistaken for a newly stored lead. For rate-limit handling, use a
separate low-limit local test configuration, submit only a bounded number of
requests, verify HTTP 429 and a friendly message, then restore the setting.

**CORS is a separate check:** the default dev proxy is same-origin and cannot
prove API CORS. Temporarily set the fictional public config's `api.baseUrl` to
`http://localhost:8080`, reload, and submit from the allowed `localhost:4200`
origin. Expect success and the matching `Access-Control-Allow-Origin` response.
Then open the preview from an unlisted origin (for example `127.0.0.1:4200`,
if reachable and not allowlisted); the browser must block the cross-origin
request and the app must not show success. Restore `api.baseUrl` afterward.
Never put admin credentials in DevTools/public config.

Check database persistence, not just a UI success message:

```powershell
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d orchbiz -c "select count(*) from leads;"'
docker compose restart postgres lead-api n8n
docker compose ps
```

Wait for health/startup, then re-read the same lead and draft with the admin
API and check published workflows remain available. Record before/after IDs.
There is currently no committed Playwright/axe suite; a manual checklist is
not evidence that an automated browser suite or CI integration exists.

## 10. Client checks, hygiene and shutdown

For an actual authorized engagement only, use its existing slug and validated
artifacts. Run individually:

```powershell
.\tools\orchbiz.ps1 check-scope <client-slug>
.\tools\orchbiz.ps1 check-budget <client-slug>
.\tools\orchbiz.ps1 validate-client <client-slug>
.\tools\orchbiz.ps1 check-gate <client-slug> gate1
.\tools\orchbiz.ps1 check-gate <client-slug> gate2
```

Replace `<client-slug>` before running. A pending gate is correctly **BLOCKED**,
not a defect to bypass. Never approve gates for the operator. Intake parsing
and artifact schema commands are documented in [tools usage](../tools/README.md);
don't create a fake live engagement merely to run them.

From the root, run final hygiene checks:

```powershell
.\tools\orchbiz.ps1 scan-secrets .
$LASTEXITCODE
git diff --check
$LASTEXITCODE
git status --short
```

Restore only temporary test edits you made, preserving unrelated work. Stop
`npm start` with Ctrl+C in its terminal. Stop containers without deleting data:

```powershell
docker compose stop
Remove-Item Env:\ORCHBIZ_ADMIN_TOKEN -ErrorAction SilentlyContinue
Remove-Variable secureToken, secureWebhook, adminHeaders, hookHeaders -ErrorAction SilentlyContinue
```

Do not use `docker compose down -v`, delete databases or kill unrelated
processes. Keep `.env` and n8n keys private if retaining local data; dispose of
temporary secret exports securely.

## 11. Hand results to the agent without rerunning everything

Use this small result format in your local record:

```text
Commit / dirty status:
Device + Java / Maven / Node / PostgreSQL / n8n versions:
Check | PASS / FAIL / BLOCKED / NOT RUN | exit code | evidence location
CLI package:
API package:
Catalogue / Code-node logic:
Website tests / build / schemas:
PostgreSQL / API smoke:
NBO / CLE / DFU live executions:
Browser / CORS / accessibility / persistence:
Secret scan / diff check:
Remaining cases:
```

Give the agent the failing command, exit code and relevant error excerpt only.
Ask it to diagnose or change code without executing the full suite. After a
fix, rerun the smallest affected check manually; run the full checklist at a
planned milestone. Missing evidence remains unverified. These checks do not
replace Gate 2, real staging SMTP, staff authentication, scheduling/reminders
implementation, client sign-off or deployment approval.
