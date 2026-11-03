# OrchBiz Lead API

Implements the catalogue module `capture-and-store-lead` and the server side of
`enquiry-form`. Website forms POST leads here. The API validates them, removes duplicates,
stores them in PostgreSQL per tenant, and queues a `lead.created` event. A background
dispatcher sends that event to the n8n `notify-business-owner` workflow.

```
website form ──POST /api/leads──▶ lead-api ──▶ PostgreSQL (tenants, leads, lead_events outbox)
                                      └── dispatcher ──POST + X-OrchBiz-Token──▶ n8n webhook
```

## Build and test (Java 17 + Maven)

```powershell
mvn -B -f platform\lead-api\pom.xml package      # runs 18 tests on in-memory H2 (PostgreSQL mode)
java -jar platform\lead-api\target\lead-api.jar  # needs a reachable PostgreSQL
```

## Configuration (environment variables only, never commit values)

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection | `jdbc:postgresql://localhost:5432/orchbiz`, `orchbiz`, empty |
| `ORCHBIZ_ADMIN_TOKEN` | Bearer token for `/api/admin/**` (admin returns 503 while empty) | empty |
| `ORCHBIZ_ALLOWED_ORIGINS` | Comma-separated website origins allowed to call `POST /api/leads` from a browser | empty (CORS closed) |
| `ORCHBIZ_HONEYPOT_FIELD` | Hidden spam-trap field name | `_hp` |
| `LEAD_EVENTS_WEBHOOK_URL` | n8n webhook URL(s), comma-separated, e.g. notify + classify (events stay `PENDING` while empty) | empty |
| `LEAD_EVENTS_WEBHOOK_TOKEN` | Sent as `X-OrchBiz-Token`; must match the n8n Header Auth credential | empty |
| `LEAD_EVENTS_DISPATCH_INTERVAL_MS`, `LEAD_EVENTS_MAX_ATTEMPTS` | Delivery loop tuning | `5000`, `5` |
| `ORCHBIZ_RATE_LIMIT_PER_MINUTE` | Max `POST /api/leads` per client IP per minute (`0` disables). Excess requests get `429` with a `Retry-After` header | `30` |
| `FORWARD_HEADERS_STRATEGY` | Set to `native` behind a trusted reverse proxy so the rate limit uses the real client IP | `none` |
| `PORT` | HTTP port | `8080` |

Flyway creates the schema on startup (`src/main/resources/db/migration`).

## Endpoints

### `POST /api/leads` (public)

Payload contract from `catalogue/websites/enquiry-form/manifest.json`:

```json
{ "tenantId": "apex-coaching", "submittedAt": "2026-11-02T15:30:00+05:30", "sourcePage": "/contact",
  "fields": { "full_name": "…", "phone": "…", "email": "…", "course_interest": "…" } }
```

| Response | Meaning |
|---|---|
| `201` `{leadId, tenantId, duplicate:false, storedIn:["postgresql"]}` | New lead stored, notification queued |
| `200` `{…, duplicate:true}` | Same email/phone for this tenant within its dedupe window. The fields are merged into the existing lead and no new notification is sent. |
| `202` `{accepted:true}` | Honeypot filled: discarded silently |
| `400` `{error:"validation_failed", fieldErrors:{…}}` | Missing required field, invalid email/phone/timestamp, bad field name or value type |
| `403` `{error:"tenant_not_accepting_leads"}` | Unknown or inactive tenant (same response for both) |

### Admin (`Authorization: Bearer $ORCHBIZ_ADMIN_TOKEN`)

- `PUT /api/admin/tenants/{tenantId}`: create or update the form config with `{displayName, requiredFields[], emailField?, phoneField?, dedupeWindowHours? (0–720, default 24), active? (default true)}`.
- `GET /api/admin/tenants/{tenantId}`: read the config.
- `GET /api/admin/tenants/{tenantId}/leads?limit=50`: latest leads for that tenant only (limit 1–200), including `classificationStatus`, `classification` and `notificationStatus` (`PENDING`/`SENT`/`FAILED`).
- `PUT /api/admin/tenants/{tenantId}/leads/{leadId}/classification`: used by the `classify-enquiry` workflow. Body: `{status: classified|needs-review|pending, intent, urgency, serviceInterest, confidence}`. `intent` must be one of `enrolment, pricing, demo-request, schedule-question, support, other`; `urgency` must be one of `high, normal, low`; `confidence` must be 0–1. Labels are not required when `status` is `pending`.
- `POST /api/admin/tenants/{tenantId}/leads/{leadId}/drafts`: used by the `draft-follow-up` workflow. Body: `{channel: email|whatsapp, subject?, body (1–5000), source: template|llm}`. Drafts are always stored as `awaiting-approval`; the API never sends messages.
- `GET /api/admin/tenants/{tenantId}/leads/{leadId}/drafts`: list a lead's drafts.

A lead belonging to another tenant returns `404 lead_not_found`.

### `GET /actuator/health`

Liveness/readiness for hosting.

## Event payload sent to n8n

Headers: `X-OrchBiz-Token`, `X-OrchBiz-Event: lead.created`, `X-OrchBiz-Event-Id`.

```json
{ "event": "lead.created", "eventId": "…", "occurredAt": "…", "tenantId": "…", "tenantName": "…",
  "leadId": "…", "lead": { "fields": { … }, "sourcePage": "/contact", "receivedAt": "…" } }
```

With several webhook URLs, every target receives the event. It becomes `SENT` only when all of them return 2xx, and a retry re-sends to every target. Any 2xx response marks the event `SENT` when there is a single target. Other responses are retried until `LEAD_EVENTS_MAX_ATTEMPTS`
is reached, then the event is marked `FAILED`. To find events that need operator attention:
`SELECT * FROM lead_events WHERE status='FAILED'`.

## Known limitations

- Delivery is at-least-once and assumes a single API instance. The n8n workflow should de-duplicate on `eventId`.
- Two identical submissions arriving at the same moment could both be stored.
- Rate limiting is in-memory and per instance. Keep a reverse proxy or WAF limit in production as well.
- n8n workflows call the admin write-back endpoints with the full admin token. Scoped automation tokens are future work.
- The Docker image and PostgreSQL runtime have not yet been exercised on the build device. Tests use H2 in PostgreSQL mode.
