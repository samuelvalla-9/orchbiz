-- OrchBiz lead API schema. Portable across PostgreSQL and H2 (PostgreSQL mode) for tests.

CREATE TABLE tenants (
    id                  VARCHAR(64)  PRIMARY KEY,
    display_name        VARCHAR(200) NOT NULL,
    required_fields     VARCHAR(2000) NOT NULL DEFAULT '',
    email_field         VARCHAR(64)  NOT NULL DEFAULT 'email',
    phone_field         VARCHAR(64)  NOT NULL DEFAULT 'phone',
    dedupe_window_hours INTEGER      NOT NULL DEFAULT 24,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE leads (
    id                  UUID         PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL REFERENCES tenants (id),
    email_norm          VARCHAR(320),
    phone_norm          VARCHAR(32),
    fields_json         TEXT         NOT NULL,
    source_page         VARCHAR(500),
    client_submitted_at TIMESTAMP WITH TIME ZONE,
    first_received_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_received_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    submission_count    INTEGER      NOT NULL DEFAULT 1,
    status              VARCHAR(32)  NOT NULL DEFAULT 'NEW'
);

CREATE INDEX idx_leads_tenant_email ON leads (tenant_id, email_norm);
CREATE INDEX idx_leads_tenant_phone ON leads (tenant_id, phone_norm);
CREATE INDEX idx_leads_tenant_received ON leads (tenant_id, last_received_at);

-- Transactional outbox: written in the same transaction as the lead, delivered to n8n by EventDispatcher.
CREATE TABLE lead_events (
    id           UUID         PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL REFERENCES tenants (id),
    lead_id      UUID         NOT NULL REFERENCES leads (id),
    event_type   VARCHAR(64)  NOT NULL,
    payload_json TEXT         NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    last_error   VARCHAR(1000),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_lead_events_status ON lead_events (status, created_at);
