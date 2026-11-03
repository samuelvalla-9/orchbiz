-- classify-enquiry writes here; draft-follow-up stores drafts that staff must approve before anything is sent.

ALTER TABLE leads ADD COLUMN classification_status VARCHAR(16);
ALTER TABLE leads ADD COLUMN classification_json TEXT;
ALTER TABLE leads ADD COLUMN classified_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE follow_up_drafts (
    id          UUID         PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL REFERENCES tenants (id),
    lead_id     UUID         NOT NULL REFERENCES leads (id),
    channel     VARCHAR(16)  NOT NULL,
    subject     VARCHAR(300),
    body        TEXT         NOT NULL,
    source      VARCHAR(16)  NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_drafts_tenant_lead ON follow_up_drafts (tenant_id, lead_id);
