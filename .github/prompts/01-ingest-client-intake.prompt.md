---
description: Initialize a client engagement, analyze its questionnaire, and prepare requirements and architecture for Gate 1 review.
---

Ingest a new OrchBiz client intake and stop at Gate 1.

1. Ask for the `<client-slug>` and path to the completed `.xlsx` or `.csv` questionnaire if either is not given. Do not treat `intake/questionnaire-template.csv` as real customer data unless the operator explicitly states this is a trial run.
2. Verify the client slug is lowercase kebab-case and the intake file is inside `clients/<client-slug>/intake/`. Do not overwrite any existing engagement data. If `state.json` does not exist, run `.\tools\orchbiz.ps1 init-client <client-slug> --business-name "<name>"`, then set the real business name/package once established.
3. Run `.\tools\orchbiz.ps1 parse-intake <client-slug>` (add the file path if needed). It enforces `ai_data_consent === true` and validates against `intake/intake.schema.json`, writing `intake/intake.validated.json` only on success. If it exits non-zero, turn every `FAIL` line into a blocking item in `spec/clarification-questions.md`, set status `AWAITING_CLIENT_CLARIFICATION`, explain the blocker, and stop before AI processing or architecture.
4. Run the Business Analyst work using `agents/prompts/01-business-analyst.md`: run `.\tools\orchbiz.ps1 check-scope <client-slug>`, then create `spec/requirements-brief.md` and `spec/clarification-questions.md`, flagging out-of-scope requests and add-ons exactly as the CLI reports them.
5. If there are no blocking clarification questions, run the Solution Architect work using `agents/prompts/02-solution-architect.md`: run `.\tools\orchbiz.ps1 validate-catalogue`, select only package-approved catalogue modules at their manifest versions, copy `.\tools\orchbiz.ps1 check-budget <client-slug> --json` into the plan's `costEstimate`, and produce `spec/architecture-plan.json` and `spec/technical-spec.md`. Validate with `.\tools\orchbiz.ps1 validate architecture-plan clients/<client-slug>/spec/architecture-plan.json`. Halt and report if cost exceeds budget or a dependency/scope issue prevents a valid plan.
6. Update `state.json` stage statuses, `updatedAt`, status, and `auditLog` to reflect actual actions, then run `.\tools\orchbiz.ps1 validate state clients/<client-slug>/state.json`. Do not mark approvals as approved.
7. Present the requirements brief, selected modules, integrations, cost estimate, unresolved warnings and Gate 1 review checklist. Stop here. Stage 3 is prohibited until the operator explicitly sets `approvals.gate1_scope_and_architecture.approved` to `true` in the client's state.

Follow `.github/copilot-instructions.md`, `docs/agent-workflow.md`, `docs/git-workflow.md`, and the two canonical role contracts throughout. Do not commit client engagement files.
