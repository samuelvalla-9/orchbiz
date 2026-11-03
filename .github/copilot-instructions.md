# OrchBiz Workspace Instructions

## Workspace Operating Model
- Customer requirements arrive as an Excel (`.xlsx`) or CSV (`.csv`) questionnaire in `clients/<client-slug>/intake/`.
- Normalize the questionnaire to `intake/intake.validated.json` and validate it against `intake/intake.schema.json`. The CSV column template is `intake/questionnaire-template.csv`.
- Follow `docs/agent-workflow.md` and the canonical specialist contracts in `agents/prompts/`. Use catalogue package and module manifests as the source of scope and approved implementation choices.

## Deterministic Tooling (AI proposes, code validates)
- Use the OrchBiz CLI for every check it covers: `.\tools\orchbiz.ps1 <command>` from the workspace root (Java 17 + Maven; see `tools/README.md`). Commands: `init-client`, `parse-intake`, `check-scope`, `check-budget`, `check-gate`, `validate`, `validate-client`, `validate-catalogue`, `scan-secrets`.
- Exit `0` = pass, `1` = check failed/blocked, `2` = usage/environment error. Treat any non-zero exit as a blocker, quote its `FAIL` lines, and never replace a CLI check with your own judgement or hand-calculation.
- Output artifacts must validate against `schemas/*.schema.json` (`state`, `architecture-plan`, `website-copy`, `site-config`, `workflows-config`, `qa-report`).

## Test Resource Warning and Manual Execution
- Before starting a full test catalogue, Maven package/test suite, dependency install, container integration run or repeated broad validation, warn the developer that it can consume substantial CPU, memory, time and agent tokens (especially when polling or reading verbose logs). Recommend manual execution using `docs/manual-test-runbook.md` and obtain explicit confirmation before running it through the agent. An explicit request to run that specific check authorizes it; still give the warning first.
- Default broad test execution to the developer's terminal. Do not automatically start, poll, parallelize or repeat heavyweight suites. For documentation-only changes, do not run builds or tests unless documentation tests exist.
- Prefer the smallest relevant check when agent execution is authorized; distinguish the lightweight `validate-catalogue` command from full suites, while warning that its wrapper may build a missing/stale CLI jar. Do not present every catalogue validation as inherently expensive.
- Ask for command, exit code and concise failure excerpts rather than entire logs. Manually supplied results must identify their source; skipped, blocked or unexecuted checks must never be reported as passed. Manual execution changes who runs the checks, not the required validation, CLI rules or human approval gates.

## Client State Tracking
- Each engagement is isolated under `clients/<client-slug>/`.
- Initialize its `state.json` with `.\tools\orchbiz.ps1 init-client <client-slug>`; update stage status, timestamps, approvals and `auditLog` as work progresses, and keep it valid against `schemas/state.schema.json`.
- Never use another client's data or the sample intake as live customer input.

## Human Approval Gates
- **Gate 1 — `gate1_scope_and_architecture`:** after Stage 2, the operator reviews the requirements, selected modules, technical specification and budget. Stage 3 (Brand & Content, Website Builder, Workflow Engineer) must not start until `.\tools\orchbiz.ps1 check-gate <client-slug> gate1` exits `0`.
- **Gate 2 — `gate2_release_and_deploy`:** after Stage 4 QA, the operator reviews the release checklist, staging preview and client sign-off. Stage 5 (Deployment & Handover) must not start until `.\tools\orchbiz.ps1 check-gate <client-slug> gate2` exits `0`.
- Agents must never set either approval to true on behalf of the operator.

## Non-Negotiable Guardrails
- Reuse only modules from `catalogue/` included in the customer's purchased package; do not invent bespoke architectures outside scope.
- Never fabricate testimonials, pass rates, awards, accreditations or unverified customer claims.
- Never commit files under `clients/<client-slug>/` except `clients/_template/`.
- Never hardcode API keys, tokens, passwords or other secrets. Use credential references and templates.
- Do not send live external messages, provision paid API credentials, edit live DNS, or deploy production without explicit human approval and operator execution.
- Follow `docs/git-workflow.md` for all Git operations; use Git CLI only, never the IDE's Source Control UI.
