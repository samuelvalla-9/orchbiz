# OrchBiz Tools

Deterministic checks that back the agent pipeline ("AI proposes, code validates").

## Requirements
- Java 17+ and Maven 3.9+ on `PATH`. The first run of `tools\orchbiz.ps1` builds `tools/orchbiz-cli/target/orchbiz-cli.jar` and rebuilds it when sources change.

## Usage

```powershell
.\tools\orchbiz.ps1 --help
.\tools\orchbiz.ps1 <command> --help
```

Git Bash / POSIX: `tools/orchbiz <command>`. Commands run relative to the workspace root.

| Command | What it does | Used by |
|---|---|---|
| `init-client <slug> [--business-name N] [--package P]` | Creates `clients/<slug>/{intake,spec,deliverables/*,qa/evidence,handover}` and `state.json` from the template. Refuses to overwrite. | Operator, Orchestrator |
| `parse-intake <slug> [file] [--dry-run]` | Reads `.csv`/`.xlsx`, splits list fields, converts consent to boolean, derives budget amount/currency, validates against `intake/intake.schema.json`, writes `intake/intake.validated.json` only if valid. | Business Analyst |
| `check-scope <slug>` | Classifies requested modules as included / add-on / out-of-scope for the package and checks manifests exist. | Business Analyst, Architect |
| `check-budget <slug> [--json]` | Sums package base cost + module `estimatedMonthlyCostUSD`, converts the client budget using `catalogue/fx-rates.json`, fails if over budget. `--json` output goes into `architecture-plan.json` `costEstimate`. | Architect |
| `check-gate <slug> gate1\|gate2` | Fails unless the gate is approved with `approvedBy` and `approvedAt` recorded (Gate 2 also re-checks Gate 1). | Stage 3 and Stage 5 agents |
| `validate <schema> <file...>` | Validates JSON against a named schema (`intake`, `manifest`, `package`, `state`, `architecture-plan`, `website-copy`, `site-config`, `workflows-config`, `qa-report`) or a schema path. | All agents |
| `validate-client <slug> [--require a,b]` | Validates every known artifact in the client folder and checks they all belong to that client (tenant isolation). | Orchestrator, QA |
| `validate-catalogue` | Validates every module manifest and package: ids vs folders, dependencies, intake module enums, `implementation` paths, and n8n blueprint structure (inactive, credentials by `*_REF` name only). | Architect, CI |
| `scan-secrets <path...> \| --staged` | Regex scan for keys, tokens, private keys and assigned passwords. Placeholders such as `${VAR}` and `*_REF` names are allowed; a line containing `orchbiz:allow-secret` is ignored. | Workflow Engineer, QA, pre-commit hook |

Exit codes: `0` passed, `1` check failed / blocked, `2` usage or environment error.

## Pre-commit hook

```powershell
git config core.hooksPath tools/hooks
```

`tools/hooks/pre-commit` blocks staged files under `clients/` (except `clients/README.md` and `clients/_template/`) and runs `scan-secrets --staged`.

## Development

```powershell
mvn -f tools/orchbiz-cli/pom.xml package     # compile + run tests + build the jar
```

Tests in `tools/orchbiz-cli/src/test` copy the real `intake/`, `catalogue/`, `schemas/`, `clients/_template/` and `platform/` (without `target/`) into a temp folder and exercise every command against the sample intake.

CI (`.github/workflows/ci.yml`) builds and tests this CLI and `platform/lead-api`, then runs `validate-catalogue` and `scan-secrets .` on every push and pull request.
