# Git CLI Workflow & Account Isolation Policy

## 1. Mandatory Rule: CLI-Only Git Operations

All Git operations (`add`, `commit`, `branch`, `tag`, `push`, `pull`, `merge`) in the **OrchBiz** workspace **MUST** be executed exclusively through the **Git CLI**—never through the IDE's built-in Git / Source Control UI.

### Why?
- The IDE is authenticated with a separate account, whereas **OrchBiz** is linked to the personal GitHub remote (`https://github.com/samuelvalla-9/orchbiz.git`).
- Using the CLI with explicit repository-local Git identity guarantees strict account separation and ensures all commits are authored and pushed under the correct personal identity.

---

## 2. Required Git Identity for This Workspace

Every commit in this repository must use the following author and committer identity:

- **Username (`user.name`):** `samuelvalla-9`
- **Email (`user.email`):** `samuelvictorvalla@gmail.com`
- **Remote (`origin`):** `https://samuelvalla-9@github.com/samuelvalla-9/orchbiz.git` *(Must include `samuelvalla-9@` so Windows Git Credential Manager selects the `samuelvalla-9` credential instead of the default IDE/corporate account)*

### Verify / Enforce Local Repository Config

Before creating any commit or pushing, agents and operators must verify that the local repository configuration is set:

```powershell
git config --local user.name "samuelvalla-9"
git config --local user.email "samuelvictorvalla@gmail.com"
git config --local credential.username "samuelvalla-9"
git remote set-url origin "https://samuelvalla-9@github.com/samuelvalla-9/orchbiz.git"
```

To verify the active identity for this repository:

```powershell
git config --local --get user.name
git config --local --get user.email
git remote -v
```

---

## 3. Standard Agent CLI Commit & Push Procedure

Whenever an agent is instructed to commit and push changes in `orchbiz`:

### Step 1: Pre-Commit Security & Hygiene Check
1. Run `git status` via the CLI to inspect all modified and untracked files.
2. Verify that **no** files under `clients/*` (other than `clients/README.md` and `clients/_template/**`), `.env*`, credentials, or customer PII are staged.

### Step 2: Stage & Commit via CLI (Explicit Identity Enforcement)
Always include `-c user.name="samuelvalla-9" -c user.email="samuelvictorvalla@gmail.com"` or verify `--local` config before committing:

```powershell
git add .
git -c user.name="samuelvalla-9" -c user.email="samuelvictorvalla@gmail.com" commit -m "<type>: <concise summary of changes>"
```

### Step 3: Verify Commit Author
Confirm the commit was recorded with `samuelvalla-9 <samuelvictorvalla@gmail.com>`:

```powershell
git log -n 1 --format="Author: %an <%ae>%nCommitter: %cn <%ce>"
```

### Step 4: Push to Remote via CLI

First, attempt a standard CLI push:

```powershell
git push -u origin main
```

#### Zscaler / Network Proxy Fallback (`git zpush` via CLI)
If the network's Zscaler proxy blocks `POST /samuelvalla-9/orchbiz.git/git-receive-pack` with `HTTP 403 Forbidden (Server: Zscaler/6.2)`, use the globally installed `git zpush` CLI command (backed by `C:\Users\2478356\.git-tools\git-zpush.ps1`), which pushes the Git commit tree atomically over the **GitHub Git Data REST API (`api.github.com`)** using the stored CLI credential (`gh:github.com:samuelvalla-9`) and syncs the local branch ref:

```powershell
# Option A: Push already-committed local changes
git zpush

# Option B: Stage all, commit as samuelvalla-9 <samuelvictorvalla@gmail.com>, and push in one step
git zpush -m "feat: your commit message"
```

---

## 4. Guardrails for Agents

- **NEVER** instruct the user to use the IDE's Source Control tab to commit or push.
- **NEVER** modify `--global` Git configuration (which may affect other projects on the machine); always use `--local` or inline `-c` flags scoped strictly to `orchbiz`.
- **NEVER** force-push (`git push --force`) to `main` without explicit user instruction.
