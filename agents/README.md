# Agent Roster & Orchestration Guide

In **OrchBiz**, agents replace heavyweight internal back-office software. When you drop a customer's completed Google Form / Excel intake file into `clients/<client-slug>/intake/`, the **Orchestrator Agent** coordinates the specialist agents below.

## Agent Roster

| ID | Agent Role | Priority | Core Responsibility | Output Location |
|---|---|---|---|---|
| `00` | **[Project Orchestrator](prompts/00-orchestrator.md)** | Essential | Owns `state.json`, parses Excel intake, routes tasks to specialists, and enforces human approval gates. | `clients/<slug>/state.json` |
| `01` | **[Business Analyst Agent](prompts/01-business-analyst.md)** | Essential | Validates Excel intake against `intake.schema.json`, structures requirements, and flags missing info or scope creep. | `clients/<slug>/spec/requirements-brief.md` |
| `02` | **[Solution Architect Agent](prompts/02-solution-architect.md)** | Essential | Maps requirements to approved `catalogue/` templates, defines data/webhook flows, and verifies budget compliance. | `clients/<slug>/spec/architecture-plan.json` |
| `03` | **[Brand & Content Agent](prompts/03-brand-and-content.md)** | Specialist | Generates website copy, FAQs, brand tokens, and social calendars from intake facts. Never invents testimonials. | `clients/<slug>/deliverables/content/` |
| `04` | **[Website Builder Agent](prompts/04-website-builder.md)** | Specialist | Injects approved copy and brand tokens into the selected `catalogue/websites/` template and prepares a preview. | `clients/<slug>/deliverables/website/` |
| `05` | **[Workflow Engineer Agent](prompts/05-workflow-engineer.md)** | Specialist | Configures parameterized n8n workflow blueprints from `catalogue/automations/` and defines webhook test cases. | `clients/<slug>/deliverables/automations/` |
| `06` | **[QA & Security Agent](prompts/06-qa-and-security.md)** | Essential | Cross-checks deliverables against intake requirements, inspects test evidence, checks secret hygiene, and gates release. | `clients/<slug>/qa/qa-report.json` |
| `07` | **[Deployment & Handover Agent](prompts/07-deployment-and-handover.md)** | Specialist | Produces staging/production deployment checklist, DNS guide, and customer handover documentation. | `clients/<slug>/handover/` |

---

## Note on the "Operations & Optimization Agent" (Role #8 in Blueprint)
The original blueprint included an 8th **Operations & Optimization Agent** for ongoing post-delivery metric analysis and maintenance reports. Per the Phase 1–3 roadmap, this role is **deferred until Phase 4** (once live customers are running on maintenance retainers) and is documented inside `07-deployment-and-handover.md` as a post-launch runbook step.
