---
description: Run the OrchBiz QA audit, present Gate 2 for human review, and prepare handover only after recorded approval.
---

Run Stage 4 QA for an OrchBiz client and prepare Stage 5 handover only if Gate 2 is explicitly approved.

1. Ask for `<client-slug>` if it is not given. Read the client's `state.json`, validated intake, requirements, architecture and all deliverables.
2. Run `.\tools\orchbiz.ps1 validate-client <client-slug> --require state,intake,architecture-plan,website-copy,site-config,workflows-config`. If required Stage 1-3 artifacts are missing or invalid, report the `FAIL` lines and stop rather than implying they passed.
3. Run QA using `agents/prompts/06-qa-and-security.md`. Run `.\tools\orchbiz.ps1 scan-secrets clients/<client-slug>/deliverables` and inspect actual schema and test evidence, requirement traceability, unverified/fabricated claims, tenant isolation and recorded consent. Create `qa/qa-report.json` and `qa/release-checklist.md`; claimed test success without evidence is FAILED. Validate with `.\tools\orchbiz.ps1 validate qa-report clients/<client-slug>/qa/qa-report.json`.
4. Update QA stage status, overall state, `updatedAt`, and `auditLog` based on actual results. Never mark Gate 2 approved. If QA fails, show defects and route them to the responsible specialist; do not proceed to handover.
5. Present the QA report, staging preview and client sign-off requirements as the Gate 2 review. Stop and wait for an operator to update `approvals.gate2_release_and_deploy.approved` to `true` in `state.json`. An affirmative chat message alone does not replace the required state approval.
6. Only after rereading the state and verifying that `.\tools\orchbiz.ps1 check-gate <client-slug> gate2` exits `0`, run `agents/prompts/07-deployment-and-handover.md` to produce `handover/deployment-runbook.md`, `client-handover-guide.md`, and `maintenance-and-rollback.md`. These are instructions only; do not purchase hosting, modify DNS, deploy, activate live messaging or claim a deployment occurred.
7. Update the deployment/handover stage and `auditLog` only for work actually completed, then report output paths and any operator-only actions.

Follow `.github/copilot-instructions.md`, `docs/agent-workflow.md`, and `docs/git-workflow.md`. Do not commit client engagement files.
