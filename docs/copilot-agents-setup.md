# GitHub Copilot Custom Agents Setup Guide (For VM / IDE)

> **How to use this file on your Virtual Machine IDE:**  
> Open GitHub Copilot Chat in your IDE (VS Code / Visual Studio / JetBrains) and send this prompt:  
> **`Read docs/copilot-agents-setup.md and execute all steps to set up all OrchBiz agents as selectable GitHub Copilot agents in this workspace.`**

---

## Instructions for GitHub Copilot Agent

You are running inside the user's IDE on their development machine. Your task is to configure **all 8 OrchBiz specialist agents** so that the user can **select and invoke each agent directly from the GitHub Copilot Chat Agent / Mode selector dropdown** (or via `@` / `/` commands) in their IDE.

Follow **all 5 steps** below completely and create every file listed.

---

### Step 1: Read the Canonical Agent Specifications

Read all 8 role definitions in `agents/prompts/` and the workflow protocol in `docs/agent-workflow.md`:
1. `agents/prompts/00-orchestrator.md`
2. `agents/prompts/01-business-analyst.md`
3. `agents/prompts/02-solution-architect.md`
4. `agents/prompts/03-brand-and-content.md`
5. `agents/prompts/04-website-builder.md`
6. `agents/prompts/05-workflow-engineer.md`
7. `agents/prompts/06-qa-and-security.md`
8. `agents/prompts/07-deployment-and-handover.md`
9. `docs/agent-workflow.md`
10. `intake/intake.schema.json` and `catalogue/template.schema.json`

---

### Step 2: Create Selectable GitHub Copilot Custom Agents (`.github/agents/*.agent.md`)

GitHub Copilot in VS Code discovers selectable custom agents from `.github/agents/*.agent.md` (and custom chat modes from `.github/chatmodes/*.chatmode.md`).

For **each of the 8 roles**, create a corresponding `.github/agents/<agent-slug>.agent.md` file with YAML frontmatter and detailed instructions derived from `agents/prompts/<file>.md`:

| File to Create | Agent Name (`name:`) | Purpose Summary (`description:`) | Source Prompt |
|---|---|---|---|
| `.github/agents/orchestrator.agent.md` | `orchbiz-orchestrator` | Manages client `state.json`, coordinates specialist agents, and enforces Gate 1 & Gate 2 human approvals. | `agents/prompts/00-orchestrator.md` |
| `.github/agents/business-analyst.agent.md` | `orchbiz-business-analyst` | Parses customer Excel/CSV intake files, validates against `intake/intake.schema.json`, and produces `requirements-brief.md` + `clarification-questions.md`. | `agents/prompts/01-business-analyst.md` |
| `.github/agents/solution-architect.agent.md` | `orchbiz-solution-architect` | Selects approved modules from `catalogue/`, maps data flows/webhooks, verifies budget limits, and outputs `architecture-plan.json` + `technical-spec.md`. | `agents/prompts/02-solution-architect.md` |
| `.github/agents/brand-and-content.agent.md` | `orchbiz-brand-content` | Generates `brand-voice.md`, `website-copy.json`, FAQs, and messaging templates from intake data without inventing claims or testimonials. | `agents/prompts/03-brand-and-content.md` |
| `.github/agents/website-builder.agent.md` | `orchbiz-website-builder` | Configures reusable website templates from `catalogue/websites/` using approved copy and brand tokens, outputting `site.config.json` and preview builds. | `agents/prompts/04-website-builder.md` |
| `.github/agents/workflow-engineer.agent.md` | `orchbiz-workflow-engineer` | Parameterizes n8n automation blueprints from `catalogue/automations/`, generates `env.template`, and writes `workflow-test-plan.md`. | `agents/prompts/05-workflow-engineer.md` |
| `.github/agents/qa-and-security.agent.md` | `orchbiz-qa-security` | Audits deliverables against `requirements-brief.md`, validates schemas/tests, scans for hardcoded secrets or fabricated claims, and outputs `qa-report.json` + `release-checklist.md`. | `agents/prompts/06-qa-and-security.md` |
| `.github/agents/deployment-handover.agent.md` | `orchbiz-deployment-handover` | Prepares staging/production deployment runbooks, DNS checklists, rollback plans, and `client-handover-guide.md` after Gate 2 approval. | `agents/prompts/07-deployment-and-handover.md` |

#### Required Format for Each `.github/agents/<slug>.agent.md` File

Use this exact frontmatter structure so VS Code / GitHub Copilot registers the agent in the agent picker dropdown:

```markdown
---
description: "<Concise 1-sentence description of when to select this agent>"
tools: ["changes", "codebase", "editFiles", "fetch", "findTestFiles", "problems", "runCommands", "search", "terminalLastCommand", "usages"]
---

# <Agent Title>

You are the **<Agent Title>** in the **OrchBiz** Reusable AI Service Delivery Platform.

## Reference Specification
Always read and follow your canonical role contract in [<source-file>](../../agents/prompts/<source-file>) and the workflow rules in [docs/agent-workflow.md](../../docs/agent-workflow.md).

## Inputs You Expect
...

## Exact Outputs You Must Produce
...

## Hard Guardrails
...
```

*(Compatibility Note: Also copy or mirror these files into `.github/chatmodes/<slug>.chatmode.md` if the installed VS Code version uses `.chatmode.md` in addition to `.agent.md`.)*

---

### Step 3: Create Global Workspace Instructions (`.github/copilot-instructions.md`)

Create `.github/copilot-instructions.md` so GitHub Copilot always understands the repository context regardless of which agent is active:

1. **Workspace Operating Model:** Customer requirements arrive as an Excel (`.xlsx`) or CSV (`.csv`) questionnaire dropped into `clients/<client-slug>/intake/` (matching `intake/questionnaire-template.csv` and validated against `intake/intake.schema.json`).
2. **State Tracking:** Every client engagement has an isolated folder `clients/<client-slug>/` with a `state.json` file cloned from `clients/_template/state.json`.
3. **Human Approval Gates:**
   - **Gate 1 (`gate1_scope_and_architecture`):** Must be `approved: true` in `state.json` after Stage 2 (Solution Architect) before Stage 3 (Brand/Content, Website Builder, Workflow Engineer) can run.
   - **Gate 2 (`gate2_release_and_deploy`):** Must be `approved: true` in `state.json` after Stage 4 (QA & Security) before Stage 5 (Deployment & Handover) can run.
4. **Non-Negotiable Guardrails:**
   - Reuse modules from `catalogue/`—never invent bespoke architectures outside the purchased package.
   - Never fabricate customer testimonials, pass rates, or unverified claims.
   - Never commit files under `clients/<client-slug>/` (except `clients/_template/`) or hardcode API keys/secrets.
   - Follow `docs/git-workflow.md` for all Git operations (CLI-only).

---

### Step 4: Create Reusable Slash Prompt Workflows (`.github/prompts/*.prompt.md`)

Create the following prompt files in `.github/prompts/` so the user can also trigger end-to-end workflow stages with `/` commands in GitHub Copilot Chat:

1. `.github/prompts/01-ingest-client-intake.prompt.md` — Prompts for `<client-slug>` and the path to the Excel/CSV questionnaire, initializes `clients/<client-slug>/state.json`, runs the **Business Analyst** and **Solution Architect** steps, and stops at **Gate 1** for review.
2. `.github/prompts/02-generate-deliverables.prompt.md` — Verifies Gate 1 is approved in `clients/<client-slug>/state.json`, then runs **Brand & Content**, **Website Builder**, and **Workflow Engineer** deliverables generation.
3. `.github/prompts/03-run-qa-and-handover.prompt.md` — Runs the **QA & Security** audit, presents the **Gate 2** checklist, and (once approved) generates the **Deployment & Handover** package.

---

### Step 5: Configure `.vscode/settings.json` to Enable Custom Agents & Prompt Files

Create or update `.vscode/settings.json` with the settings required for VS Code GitHub Copilot Chat to load `.github/agents`, `.github/chatmodes`, `.github/prompts`, and `.github/copilot-instructions.md`:

```json
{
  "chat.agent.enabled": true,
  "chat.modeFiles": true,
  "chat.promptFiles": true,
  "github.copilot.chat.codeGeneration.useInstructionFiles": true,
  "chat.instructionsFilesLocations": {
    ".github/copilot-instructions.md": true,
    ".github/agents": true
  },
  "chat.modeFilesLocations": {
    ".github/agents": true,
    ".github/chatmodes": true
  },
  "chat.promptFilesLocations": {
    ".github/prompts": true
  }
}
```

---

### Step 6: Verification Summary

After creating all files above, report back to the user with:
1. A checklist of all created `.github/agents/*.agent.md`, `.github/chatmodes/*.chatmode.md`, `.github/prompts/*.prompt.md`, `.github/copilot-instructions.md`, and `.vscode/settings.json` files.
2. Quick instructions on how to open the **Copilot Chat Mode / Agent dropdown** in the IDE and select any of the 8 `orchbiz-*` agents.
