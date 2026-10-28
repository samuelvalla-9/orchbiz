# Client Workspaces (`clients/`)

Every customer engagement is strictly isolated in its own directory: `clients/<client-slug>/`.

> **Security & Privacy Rule:** All real customer directories (`clients/*` except `README.md` and `_template/`) are ignored by `.gitignore` so that raw intake spreadsheets, customer PII, and client deliverables are never accidentally committed to the shared template repository.

---

## Standard Per-Client Folder Structure

When onboarding a new customer, copy `clients/_template/` to `clients/<client-slug>/`:

```text
clients/<client-slug>/
├── state.json                         # Managed by Orchestrator Agent (phase, approvals, audit log)
├── intake/
│   ├── response.xlsx                  # Raw Google Form / Excel questionnaire response
│   └── intake.validated.json          # Normalized & schema-validated intake (by Business Analyst Agent)
├── spec/
│   ├── requirements-brief.md          # Structured scope & requirement summary
│   ├── clarification-questions.md     # Missing or ambiguous items (if any)
│   ├── architecture-plan.json         # Selected catalogue templates, integrations & cost estimate
│   └── technical-spec.md              # Architecture diagram & Gate 1 review document
├── deliverables/
│   ├── content/                       # Brand voice, website-copy.json, FAQs, message templates
│   ├── website/                       # Configured site.config.json & preview instructions
│   └── automations/                   # Parameterized n8n workflows, env.template & test plan
├── qa/
│   ├── qa-report.json                 # Traceability, test evidence & security audit report
│   └── release-checklist.md           # Gate 2 human sign-off checklist
└── handover/
    ├── deployment-runbook.md          # Production cutover & DNS steps
    ├── client-handover-guide.md       # Customer-facing user guide
    └── maintenance-and-rollback.md    # Backup & rollback procedures
```
