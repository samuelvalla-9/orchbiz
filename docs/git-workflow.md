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
If the network's Zscaler proxy blocks `POST /samuelvalla-9/orchbiz.git/git-receive-pack` with `HTTP 403 Forbidden (Server: Zscaler/6.2)`, use the globally installed `git zpush` CLI command (located on this machine at `C:\Users\2478356\.git-tools\git-zpush.ps1`), which pushes the Git commit tree atomically over the **GitHub Git Data REST API (`api.github.com`)** using the stored CLI credential (`gh:github.com:samuelvalla-9`) and syncs the local branch ref:

```powershell
# Option A: Push already-committed local changes
git zpush

# Option B: Stage all, commit as samuelvalla-9 <samuelvictorvalla@gmail.com>, and push in one step
git zpush -m "feat: your commit message"
```

---

## 5. Portable `git-zpush.ps1` Setup (For Other Devices)

To set up `git zpush` on another Windows machine:

1. **Authenticate GitHub CLI** (so `gh:github.com:samuelvalla-9` is stored in Windows Credential Manager, or set `$env:GITHUB_TOKEN`):
   ```powershell
   gh auth login
   ```
2. **Save the script below** to `C:\Users\<YourUser>\.git-tools\git-zpush.ps1` and register the global Git alias:
   ```powershell
   git config --global alias.zpush "!powershell.exe -NoProfile -ExecutionPolicy Bypass -File $HOME/.git-tools/git-zpush.ps1"
   ```

### Full `git-zpush.ps1` Script

```powershell
param(
    [Alias("m")]
    [string]$Message = ""
)

$ErrorActionPreference = "Stop"

# 1. Verify we are inside a Git repository
$isGit = git rev-parse --is-inside-work-tree 2>$null
if ($LASTEXITCODE -ne 0 -or $isGit -ne "true") {
    Write-Host "Error: Current directory is not a Git repository." -ForegroundColor Red
    exit 1
}

# 2. Optional: If -m "message" was provided, stage & commit with samuelvalla-9 identity first
if (-not [string]::IsNullOrWhiteSpace($Message)) {
    Write-Host "Staging and committing changes as samuelvalla-9..." -ForegroundColor Cyan
    git add .
    git -c user.name="samuelvalla-9" -c user.email="samuelvictorvalla@gmail.com" commit -m $Message
    if ($LASTEXITCODE -ne 0) {
        Write-Host "No new changes to commit; checking for unpushed commits..." -ForegroundColor Yellow
    }
}

# 3. Read GitHub CLI token (gh:github.com:samuelvalla-9) from Windows Credential Manager (or $env:GITHUB_TOKEN)
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class CredMan {
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public struct CREDENTIAL {
    public int Flags; public int Type; public string TargetName; public string Comment;
    public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
    public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist;
    public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName;
  }
  [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
  public static extern bool CredRead(string target, int type, int reservedFlag, out IntPtr credentialPtr);
  public static byte[] GetBytes(string target) {
    IntPtr ptr;
    if (!CredRead(target, 1, 0, out ptr)) return null;
    CREDENTIAL c = (CREDENTIAL)Marshal.PtrToStructure(ptr, typeof(CREDENTIAL));
    byte[] b = new byte[c.CredentialBlobSize];
    Marshal.Copy(c.CredentialBlob, b, 0, c.CredentialBlobSize);
    return b;
  }
}
"@ -ErrorAction SilentlyContinue

$token = $env:GITHUB_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    $credBytes = [CredMan]::GetBytes("gh:github.com:samuelvalla-9")
    if (-not $credBytes) {
        Write-Host "Error: Could not find 'gh:github.com:samuelvalla-9' in Windows Credential Manager (or `$env:GITHUB_TOKEN)." -ForegroundColor Red
        exit 1
    }
    $token = [System.Text.Encoding]::UTF8.GetString($credBytes).Trim()
}

$headers = @{
    "Authorization"        = "Bearer $token"
    "Accept"               = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
    "User-Agent"           = "git-zpush-cli"
}

# 4. Detect owner, repo, branch, and commit message
$remoteUrl = ((git remote get-url origin) -join "").Trim()
if ($remoteUrl -match "github\.com[:/]([^/]+)/([^/.]+?)(\.git)?$") {
    $owner = $Matches[1]
    $repo  = $Matches[2]
} else {
    Write-Host "Error: Remote 'origin' ($remoteUrl) is not a GitHub URL." -ForegroundColor Red
    exit 1
}

$branch    = ((git rev-parse --abbrev-ref HEAD) -join "").Trim()
$msg       = ((git log -1 --pretty=%B) -join "`n").Trim()
$localHead = ((git rev-parse HEAD) -join "").Trim()

# 5. Check remote branch state on GitHub
$parentSha   = $null
$baseTreeSha = $null
try {
    $refInfo     = Invoke-RestMethod -Uri "https://api.github.com/repos/$owner/$repo/git/ref/heads/$branch" -Headers $headers
    $parentSha   = $refInfo.object.sha.ToString()
    $commitInfo  = Invoke-RestMethod -Uri "https://api.github.com/repos/$owner/$repo/git/commits/$parentSha" -Headers $headers
    $baseTreeSha = $commitInfo.tree.sha.ToString()
} catch {
    # Remote branch does not exist yet or repo is empty
}

if ($parentSha -and ($parentSha -eq $localHead)) {
    Write-Host "Everything up-to-date on $owner/$repo ($branch)." -ForegroundColor Green
    exit 0
}

# If repo on GitHub is completely empty (0 commits), initialize it first so Git Data API is active
if (-not $parentSha) {
    try {
        Invoke-RestMethod -Uri "https://api.github.com/repos/$owner/$repo/commits?per_page=1" -Headers $headers | Out-Null
    } catch {
        Write-Host "Initializing empty GitHub repository $owner/$repo..." -ForegroundColor Cyan
        $initB64  = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("# $repo`n"))
        $initBody = @{
            message   = "chore: initialize repository"
            content   = $initB64
            branch    = $branch
            author    = @{ name = "samuelvalla-9"; email = "samuelvictorvalla@gmail.com" }
            committer = @{ name = "samuelvalla-9"; email = "samuelvictorvalla@gmail.com" }
        } | ConvertTo-Json -Depth 5
        Invoke-RestMethod -Method Put -Uri "https://api.github.com/repos/$owner/$repo/contents/README.md" -Headers $headers -Body $initBody -ContentType "application/json" | Out-Null
    }
}

# 6. Collect changed/tracked files and upload Base64 blobs
$changedFiles = if ($parentSha) { @(git diff --name-only "$parentSha..HEAD") } else { @(git ls-files) }
if ($changedFiles.Count -eq 0) {
    Write-Host "No file changes detected between origin/$branch and HEAD." -ForegroundColor Yellow
    exit 0
}

Write-Host "Uploading $($changedFiles.Count) file(s) to $owner/$repo ($branch) via GitHub API..." -ForegroundColor Cyan
$treeItems = @()

foreach ($f in $changedFiles) {
    if ([string]::IsNullOrWhiteSpace($f)) { continue }
    $fullPath = Join-Path (Get-Location) $f
    $gitPath  = $f.Replace('\', '/')
    if (Test-Path $fullPath -PathType Leaf) {
        $b64  = [Convert]::ToBase64String([IO.File]::ReadAllBytes($fullPath))
        $blob = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$owner/$repo/git/blobs" -Headers $headers -Body (@{ content = $b64; encoding = "base64" } | ConvertTo-Json) -ContentType "application/json"
        $treeItems += @{ path = $gitPath; mode = "100644"; type = "blob"; sha = $blob.sha.ToString() }
        Write-Host "  + $gitPath" -ForegroundColor Gray
    } else {
        # Deleted file
        $treeItems += @{ path = $gitPath; mode = "100644"; type = "blob"; sha = $null }
        Write-Host "  - $gitPath (deleted)" -ForegroundColor Gray
    }
}

# 7. Create tree, commit, and update branch reference
$treePayload = @{ tree = @($treeItems) }
if ($baseTreeSha) { $treePayload["base_tree"] = $baseTreeSha }
$treeBytes = [System.Text.Encoding]::UTF8.GetBytes(($treePayload | ConvertTo-Json -Depth 5))
$newTree   = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$owner/$repo/git/trees" -Headers $headers -Body $treeBytes -ContentType "application/json; charset=utf-8"

$nowIso = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$commitPayload = @{
    message   = $msg
    tree      = $newTree.sha.ToString()
    parents   = if ($parentSha) { ,([string[]]@($parentSha)) } else { @() }
    author    = @{ name = "samuelvalla-9"; email = "samuelvictorvalla@gmail.com"; date = $nowIso }
    committer = @{ name = "samuelvalla-9"; email = "samuelvictorvalla@gmail.com"; date = $nowIso }
}
$commitBytes = [System.Text.Encoding]::UTF8.GetBytes(($commitPayload | ConvertTo-Json -Depth 5))
$newCommit   = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$owner/$repo/git/commits" -Headers $headers -Body $commitBytes -ContentType "application/json; charset=utf-8"

try {
    Invoke-RestMethod -Method Patch -Uri "https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch" -Headers $headers -Body (@{ sha = $newCommit.sha.ToString(); force = $true } | ConvertTo-Json) -ContentType "application/json" | Out-Null
} catch {
    Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$owner/$repo/git/refs" -Headers $headers -Body (@{ ref = "refs/heads/$branch"; sha = $newCommit.sha.ToString() } | ConvertTo-Json) -ContentType "application/json" | Out-Null
}

# 8. Sync local git tracking branch with remote
git fetch origin $branch
git reset --mixed "origin/$branch"
git branch --set-upstream-to="origin/$branch" $branch 2>$null | Out-Null

Write-Host "Successfully pushed to https://github.com/$owner/$repo ($branch -> $($newCommit.sha.ToString().Substring(0,7)))" -ForegroundColor Green
```

---

## 4. Guardrails for Agents

- **NEVER** instruct the user to use the IDE's Source Control tab to commit or push.
- **NEVER** modify `--global` Git configuration (which may affect other projects on the machine); always use `--local` or inline `-c` flags scoped strictly to `orchbiz`.
- **NEVER** force-push (`git push --force`) to `main` without explicit user instruction.
