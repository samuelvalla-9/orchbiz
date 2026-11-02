---
description: Verify Gate 1 approval and generate the approved content, website and automation deliverables.
---

Generate Stage 3 deliverables for an existing OrchBiz client engagement.

1. Ask for `<client-slug>` if it is not given. Read `clients/<client-slug>/state.json`, the validated intake, requirements brief, architecture plan and technical specification.
2. Run `.\tools\orchbiz.ps1 check-gate <client-slug> gate1`. If it does not exit `0`, explain which Gate 1 artifacts require operator review and stop without changing deliverables.
3. Run `.\tools\orchbiz.ps1 validate-client <client-slug> --require state,intake,architecture-plan`. If any required Stage 1 or Stage 2 input is missing or invalid, report the exact blocker and stop; do not infer approvals or architecture.
4. Generate the Brand & Content outputs using `agents/prompts/03-brand-and-content.md`: `deliverables/content/brand-voice.md`, `website-copy.json`, `message-templates.md`, and `unverified-claims-flag.md`; create `social-content-calendar.csv` only if purchased.
5. Generate the Website Builder outputs using `agents/prompts/04-website-builder.md`: `deliverables/website/site.config.json` and `preview-instructions.md`, using only approved modules and supplied copy/assets.
6. Generate the Workflow Engineer outputs using `agents/prompts/05-workflow-engineer.md`: `deliverables/automations/workflows-config.json`, parameterized workflow JSON files, `env.template`, and `workflow-test-plan.md`. Keep all external messaging draft/dry-run/staging; include references, never real credentials.
7. Run `.\tools\orchbiz.ps1 validate-client <client-slug> --require website-copy,site-config,workflows-config` and `.\tools\orchbiz.ps1 scan-secrets clients/<client-slug>/deliverables`; fix and re-run until both exit `0`.
8. Update each completed stage's actual status, `updatedAt`, and `auditLog` in `state.json`. Do not set Gate 2 approval. Summarize generated paths, the CLI results, and any review needed before QA.

Follow `.github/copilot-instructions.md` and `docs/agent-workflow.md`. Do not commit client engagement files.
