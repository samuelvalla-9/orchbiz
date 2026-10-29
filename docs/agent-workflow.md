# Multi-Agent Orchestration & Collaboration Workflow

This document specifies how the agents in `orchbiz` collaborate to turn a customer's **Excel / CSV Intake Questionnaire** into a tested, approved, and deployable solution.

---

## 1. Core Principles of Agent Execution

1. **Single Orchestrator, Structured Hand-offs:** The **Project Orchestrator Agent** owns `clients/<client-slug>/state.json`. Specialist agents never call each other directly; they read approved artifacts from the client directory and write structured deliverables back to the client directory.
2. **Deterministic Validation Between Steps:** Every specialist agent output is checked against a schema or checklist before the next step begins.
3. **Strict Scope Enforcement:** Agents may only select modules from `catalogue/` that are included in the customer's purchased package. Out-of-scope requests in the intake spreadsheet are flagged in `scope-warnings.md` for upsell or phase-2 deferral.
4. **Explicit Human Approval Gates:** No external communications, paid API provisioning, DNS edits, or production deployments happen automatically.

---

## 2. Step-by-Step Pipeline & Artifacts

### Stage 0: Intake Ingestion
- **Input:** `clients/<client-slug>/intake/response.xlsx` (or `.csv`)
- **Action:** Operator drops the customer's completed questionnaire into the client folder and invokes the **Orchestrator Agent**.
- **Output:** Initialized `clients/<client-slug>/state.json` (`status: "INTAKE_RECEIVED"`).

### Stage 1: Requirement Analysis (`01-business-analyst`)
- **Input:** Raw Excel/CSV intake file + [`intake/intake.schema.json`](../intake/intake.schema.json) + selected package definition from `catalogue/packages/`.
- **Responsibilities:**
  - Parse spreadsheet columns into canonical `clients/<client-slug>/intake/intake.validated.json`.
  - Check for missing mandatory inputs, contradictions, or out-of-scope requests.
  - Generate `clients/<client-slug>/spec/requirements-brief.md` and `clients/<client-slug>/spec/clarification-questions.md`.
- **Gate Check:** If `blockingQuestionsCount > 0`, the Orchestrator pauses (`status: "AWAITING_CLIENT_CLARIFICATION"`) until the operator updates the Excel/intake file.

### Stage 2: Technical Architecture & Module Mapping (`02-solution-architect`)
- **Input:** `intake.validated.json` + `requirements-brief.md` + `catalogue/` module metadata.
- **Responsibilities:**
  - Select exact template IDs and versions from `catalogue/websites/` and `catalogue/automations/`.
  - Define the data model, webhook contracts, integration list, and estimated monthly operating cost.
  - Verify estimated monthly cost $\le$ `monthly_ops_budget`.
- **Output:** `clients/<client-slug>/spec/architecture-plan.json` and `clients/<client-slug>/spec/technical-spec.md`.

---

### 🛑 GATE 1: Human Scope, Architecture & Budget Approval
- **Operator Reviews:**
  - `requirements-brief.md`
  - `technical-spec.md` (selected modules, integrations, and monthly cost estimate)
- **Action:** Operator updates `approvals.gate1_scope_and_architecture.approved = true` in `state.json` (or instructs the Orchestrator to proceed).

---

### Stage 3: Parallel Specialist Generation

Once Gate 1 is approved, three specialist streams run (sequentially or in parallel subagents):

#### Stream 3A: Brand & Content (`03-brand-and-content`)
- **Input:** `intake.validated.json` + `technical-spec.md`.
- **Outputs in `clients/<client-slug>/deliverables/content/`:**
  - `brand-voice.md` (color tokens, typography pairing, tone rules)
  - `website-copy.json` (hero headline, subheadline, CTA, service descriptions, about section, FAQs)
  - `unverified-claims-flag.md` (any claims or missing testimonials requiring client sign-off)
  - `social-content-calendar.csv` (if `social-content-calendar` module is selected)

#### Stream 3B: Website Assembly (`04-website-builder`)
- **Input:** `architecture-plan.json` + `website-copy.json` + `catalogue/websites/<template-id>/`.
- **Outputs in `clients/<client-slug>/deliverables/website/`:**
  - `site.config.json` (configured modules, theme colors, form fields, API endpoints)
  - Configured preview build or task implementation plan for the selected website template.

#### Stream 3C: Workflow & Automation Configuration (`05-workflow-engineer`)
- **Input:** `architecture-plan.json` + `intake.validated.json` + `catalogue/automations/<template-id>/`.
- **Outputs in `clients/<client-slug>/deliverables/automations/`:**
  - Configured n8n workflow definitions (`lead-capture.workflow.json`, `appointment-booking.workflow.json`, etc.)
  - `env.template` (list of required credential references and environment variables—never raw secrets)
  - `workflow-test-plan.md` (webhook curl payloads and expected outcomes)

### Stage 4: Quality Assurance & Security Review (`06-qa-and-security`)
- **Input:** All artifacts in `clients/<client-slug>/spec/` and `clients/<client-slug>/deliverables/`.
- **Responsibilities:**
  - Verify 100% traceability from `requirements-brief.md` to deliverables.
  - Validate JSON schemas, check for fabricated testimonials/claims, verify no hardcoded secrets or API keys exist, and verify test evidence.
- **Outputs in `clients/<client-slug>/qa/`:**
  - `qa-report.json` (`passed: true/false`, defects list, security checklist)
  - `release-checklist.md`

---

### 🛑 GATE 2: Human QA & Release Approval
- **Operator Reviews:**
  - Preview website + configured n8n workflows in staging
  - `qa/release-checklist.md`
  - Client sign-off on preview and messaging templates
- **Action:** Operator sets `approvals.gate2_release_and_deploy.approved = true` in `state.json`.

---

### Stage 5: Deployment Preparation & Client Handover (`07-deployment-and-handover`)
- **Input:** Approved deliverables + `qa/release-checklist.md`.
- **Outputs in `clients/<client-slug>/handover/`:**
  - `deployment-runbook.md` (step-by-step production deployment & DNS checklist for the operator)
  - `client-handover-guide.md` (customer-facing guide: how to view leads, manage appointments, and request support)
  - `maintenance-and-rollback.md` (backup & rollback instructions)
