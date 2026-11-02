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
| `LEAD_EVENTS_WEBHOOK_URL` | n8n webhook URL (events stay `PENDING` while empty) | empty |
| `LEAD_EVENTS_WEBHOOK_TOKEN` | Sent as `X-OrchBiz-Token`; must match the n8n Header Auth credential | empty |
| `LEAD_EVENTS_DISPATCH_INTERVAL_MS`, `LEAD_EVENTS_MAX_ATTEMPTS` | Delivery loop tuning | `5000`, `5` |
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
- `GET /api/admin/tenants/{tenantId}/leads?limit=50`: latest leads for that tenant only (limit 1–200).

### `GET /actuator/health`

Liveness/readiness for hosting.

## Event payload sent to n8n

Headers: `X-OrchBiz-Token`, `X-OrchBiz-Event: lead.created`, `X-OrchBiz-Event-Id`.

```json
{ "event": "lead.created", "eventId": "…", "occurredAt": "…", "tenantId": "…", "tenantName": "…",
  "leadId": "…", "lead": { "fields": { … }, "sourcePage": "/contact", "receivedAt": "…" } }
```

Any 2xx response marks the event `SENT`. Other responses are retried until `LEAD_EVENTS_MAX_ATTEMPTS`
is reached, then the event is marked `FAILED`. To find events that need operator attention:
`SELECT * FROM lead_events WHERE status='FAILED'`.

## Known limitations

- Delivery is at-least-once and assumes a single API instance. The n8n workflow should de-duplicate on `eventId`.
- Two identical submissions arriving at the same moment could both be stored.
- There is no rate limiting yet. Put the API behind a reverse proxy or WAF with rate limits before going live.
- The Docker image and PostgreSQL runtime have not yet been exercised on the build device. Tests use H2 in PostgreSQL mode.
