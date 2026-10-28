# Architecture Evaluation: Do We Need All 7 Applications?

## Context

The initial **Reusable AI Service Delivery Platform** blueprint proposed building **7 internal applications** on top of a shared platform core (Angular + Spring Boot + PostgreSQL) before or alongside delivering customer solutions:

1. Client Intake & Package Builder
2. Project Orchestrator
3. Website & Brand Studio
4. Automation Blueprint Studio
5. Content Production Studio
6. QA & Release Centre
7. Client Operations Portal

While the **functional separation of concerns** in that blueprint is strong, building 7 full web applications for a **one-person software services business** creates a massive engineering bottleneck before a single customer is served.

Your proposed workflow—**collecting customer requirements via a Google Form / Excel spreadsheet and feeding that file into an Orchestrator Agent that delegates to specialist agents and deterministic tools**—achieves the exact same architectural guarantees at a fraction of the build and maintenance cost.

---

## Application-by-Application Decision Matrix

### 1. Client Intake & Package Builder
- **Blueprint Proposal:** Custom web application for collecting business type, audience, goals, brand assets, budget, hosting, and module selection.
- **Decision:** **DO NOT BUILD AS A CUSTOM WEB APP.** Replace with **Google Forms + Excel/CSV (`intake/`)**.
- **Why:**
  - Google Forms / Microsoft Forms / Tally already provide a polished, mobile-friendly UI with file upload support and zero hosting or maintenance cost.
  - Alternatively, you can fill out the structured Excel template yourself during a 30-minute discovery call with the client.
  - Google Forms exports directly to `.xlsx` / `.csv`. A tiny deterministic parser (or the **Business Analyst Agent**) maps the spreadsheet row into a validated `intake.json` conforming to [`intake/intake.schema.json`](file:///c:/Users/2478356/projects/orchbiz/intake/intake.schema.json).

### 2. Project Orchestrator
- **Blueprint Proposal:** Custom web app owning project state, task queues, agent assignments, approvals, errors, costs, and audit history.
- **Decision:** **REPLACE UI WITH WORKSPACE STATE + ORCHESTRATOR AGENT (Phase 1–3).**
- **Why:**
  - As a solo operator, your IDE/workspace + Git history + a structured `clients/<client-slug>/state.json` file *is* your orchestrator dashboard.
  - The **Orchestrator Agent** ([`agents/prompts/00-orchestrator.md`](file:///c:/Users/2478356/projects/orchbiz/agents/prompts/00-orchestrator.md)) reads `state.json`, invokes the next specialist agent in sequence, enforces schema validation on its output, logs every step to `state.json`, and halts when `approvalRequired: true`.
  - **When to build a backend orchestrator:** Only in Phase 3/4 if you want unattended webhook-triggered background execution after Google Form submission.

### 3. Website & Brand Studio
- **Blueprint Proposal:** Custom visual studio to manage reusable page templates, design systems, and assemble responsive pages.
- **Decision:** **DO NOT BUILD A VISUAL STUDIO UI.** Use a **Git-Versioned Code Template Catalogue (`catalogue/websites/`) + Website Builder Agent**.
- **Why:**
  - Building a custom website builder UI (like Webflow/Framer) is a multi-year product in itself.
  - Instead, store clean, parameterized Angular / HTML / Tailwind / Astro templates in `catalogue/websites/`. Each template accepts a strongly-typed JSON configuration (`theme.json`, `content.json`, `modules.json`).
  - The **Brand & Content Agent** generates `content.json`, and the **Website Builder Agent** injects it into the selected template and spins up a local or staging preview.

### 4. Automation Blueprint Studio
- **Blueprint Proposal:** Custom studio to store and configure workflow blueprints for lead capture, notifications, follow-ups, booking, and reporting.
- **Decision:** **DO NOT BUILD A CUSTOM STUDIO UI.** Use **n8n Native UI + Parameterized JSON Blueprints (`catalogue/automations/`) + Workflow Engineer Agent**.
- **Why:**
  - **n8n** already *is* a visual Automation Studio! Building a wrapper UI around n8n duplicates existing functionality.
  - Store exported, parameterized n8n workflow `.json` files in `catalogue/automations/` alongside their `template.schema.json` contract.
  - The **Workflow Engineer Agent** takes the client's intake configuration, populates webhook paths, CRM/Google Sheet IDs, and message templates in the n8n JSON, and outputs a ready-to-import workflow file.

### 5. Content Production Studio
- **Blueprint Proposal:** Custom app for managing brand voice, content calendars, captions, scripts, post drafts, and revisions.
- **Decision:** **DO NOT BUILD A CUSTOM APP.** Use **Brand & Content Agent + Structured Markdown/CSV/Sheets Deliverables**.
- **Why:**
  - Customers prefer reviewing website copy and social calendars in Google Docs / Google Sheets / Excel or Markdown previews rather than logging into a proprietary CMS.
  - The **Brand & Content Agent** reads the intake Excel file and produces:
    - `brand-voice-guide.md`
    - `website-copy.json` (ready for the Website Builder Agent)
    - `social-content-calendar.csv` (ready to share with the customer as an Excel/Sheet file)

### 6. QA & Release Centre
- **Blueprint Proposal:** Custom application to run website, API, workflow, accessibility, security, and configuration checks.
- **Decision:** **REPLACE WITH CLI VALIDATION SUITE + QA & SECURITY AGENT.**
- **Why:**
  - Deterministic quality checks belong in automated scripts (JSON Schema validation, Playwright/Lighthouse checks, secret scanning, endpoint smoke tests).
  - The **QA & Security Agent** executes these scripts, inspects the test evidence, cross-checks every item in the customer's intake brief against the generated deliverables, and writes `qa-report.md` + `release-checklist.md`.

### 7. Client Operations Portal
- **Blueprint Proposal:** Customer-facing portal for handover docs, account links, support requests, uptime alerts, and usage metrics.
- **Decision:** **DEFER UNTIL PHASE 4 (5+ Active Retainer Clients).**
- **Why:**
  - For your first 1–5 customers, a clean **Handover Package** generated by the **Deployment & Handover Agent** (a structured PDF/Markdown/Notion guide + direct links to their Lead Dashboard and Google Form for support requests) delivers 95% of the value with 0% portal maintenance overhead.

---

## Summary: What You Actually Need to Build (Phase 1–2)

Instead of 7 internal meta-applications, you only need **two things**:

1. **This Agent-Driven Workspace (`orchbiz`)** *(Internal Factory)*
   - Google Form / Excel Questionnaire (`intake/`)
   - Specialist Agent Prompts & Orchestration Workflow (`agents/`)
   - Deterministic Schema Validators (`catalogue/template.schema.json`, `intake/intake.schema.json`)

2. **The Reusable Customer Deliverable Template (`catalogue/`)** *(The Product You Sell)*
   - **Starter Niche Offer:** *Lead-to-Appointment System for Coaching & Training Institutes*
   - A single, rock-solid, configurable codebase (Angular + Spring Boot + PostgreSQL + n8n blueprints) inside `catalogue/` that can be cloned and configured for Customer #1, #2, and #3 purely by changing configuration and content files generated from the Excel intake!
