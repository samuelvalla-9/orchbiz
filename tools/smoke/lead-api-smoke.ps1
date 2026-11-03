<#
.SYNOPSIS
  End-to-end smoke test for a running OrchBiz lead API (local, staging or Docker Compose).

.DESCRIPTION
  Runs the capture-and-store-lead contract tests (CSL-01..04) over HTTP against a dedicated smoke-test
  tenant, checks admin auth, the classification/draft write-back endpoints and (optionally) that the
  lead.created event is delivered to the configured webhook (NBO-01 path).

  The admin token is read from the ORCHBIZ_ADMIN_TOKEN environment variable, never from a parameter,
  so it does not end up in shell history. Use only against local or staging environments: it creates
  leads under the smoke-test tenant.

  Exit codes: 0 = all checks passed, 1 = a check failed, 2 = usage/environment error.

.EXAMPLE
  $env:ORCHBIZ_ADMIN_TOKEN = '<value from your local .env>'
  .\tools\smoke\lead-api-smoke.ps1 -BaseUrl http://localhost:8080
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$TenantId = 'smoke-test-tenant',
    [switch]$SkipNotification,
    [int]$NotificationTimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$token = $env:ORCHBIZ_ADMIN_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host 'ERROR ORCHBIZ_ADMIN_TOKEN environment variable is not set.'
    exit 2
}
$BaseUrl = $BaseUrl.TrimEnd('/')
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(15)
$script:failures = 0

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null, [switch]$Admin)
    $req = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::new($Method)), ($BaseUrl + $Path)
    if ($Admin) { $req.Headers.TryAddWithoutValidation('Authorization', "Bearer $token") | Out-Null }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 6 -Compress }
        $req.Content = New-Object System.Net.Http.StringContent $json, ([Text.Encoding]::UTF8), 'application/json'
    }
    try {
        $res = $client.SendAsync($req).GetAwaiter().GetResult()
    } catch {
        Write-Host "ERROR cannot reach $BaseUrl$Path : $($_.Exception.GetBaseException().Message)"
        exit 2
    }
    $text = $res.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $parsed = $null
    if ($text) { try { $parsed = $text | ConvertFrom-Json } catch { $parsed = $null } }
    [pscustomobject]@{ Status = [int]$res.StatusCode; Body = $parsed; Raw = $text }
}

function Check {
    param([string]$Id, [string]$Description, [bool]$Condition, [string]$Detail = '')
    if ($Condition) {
        Write-Host ("PASS  {0,-8} {1}" -f $Id, $Description)
    } else {
        Write-Host ("FAIL  {0,-8} {1} {2}" -f $Id, $Description, $Detail)
        $script:failures++
    }
}

function New-Lead {
    param([string]$Tenant, [hashtable]$Fields)
    @{ tenantId = $Tenant; submittedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz'); sourcePage = '/smoke-test'; fields = $Fields }
}

Write-Host "OrchBiz lead API smoke test against $BaseUrl (tenant $TenantId)"

$r = Invoke-Api GET '/actuator/health'
Check 'HEALTH' 'GET /actuator/health is UP' ($r.Status -eq 200 -and $r.Body.status -eq 'UP') "(HTTP $($r.Status))"

$r = Invoke-Api GET "/api/admin/tenants/$TenantId"
Check 'AUTH' 'admin endpoint without token is rejected' ($r.Status -eq 401) "(HTTP $($r.Status))"

$tenant = @{ displayName = 'OrchBiz Smoke Test'; requiredFields = @('full_name', 'phone', 'course_interest'); dedupeWindowHours = 24; active = $true }
$r = Invoke-Api PUT "/api/admin/tenants/$TenantId" $tenant -Admin
Check 'TENANT' 'upsert smoke-test tenant' ($r.Status -eq 200) "(HTTP $($r.Status) $($r.Raw))"
if ($r.Status -ne 200) { Write-Host 'FAIL  cannot continue without the smoke-test tenant'; exit 1 }

$rand = Get-Random -Minimum 100000000 -Maximum 999999999
$fields = @{ full_name = 'Smoke Test'; phone = "+91 9$rand"; email = "smoke-$rand@example.com"; course_interest = 'Smoke Course'; message = 'Automated smoke test, please ignore.' }

$r = Invoke-Api POST '/api/leads' (New-Lead $TenantId $fields)
Check 'CSL-01' 'valid lead is stored (201)' ($r.Status -eq 201 -and $r.Body.leadId) "(HTTP $($r.Status) $($r.Raw))"
$leadId = $r.Body.leadId

$r = Invoke-Api POST '/api/leads' (New-Lead $TenantId $fields)
Check 'CSL-02' 'repeat submission is merged as duplicate (200)' ($r.Status -eq 200 -and $r.Body.duplicate -eq $true -and $r.Body.leadId -eq $leadId) "(HTTP $($r.Status) $($r.Raw))"

$r = Invoke-Api POST '/api/leads' (New-Lead $TenantId @{ full_name = 'Smoke Test'; phone = '+91 9876543210' })
Check 'CSL-03' 'missing required field is rejected (400)' ($r.Status -eq 400 -and $r.Body.fieldErrors.course_interest) "(HTTP $($r.Status) $($r.Raw))"

$r = Invoke-Api POST '/api/leads' (New-Lead 'smoke-unknown-tenant' $fields)
Check 'CSL-04' 'unknown tenant is refused (403)' ($r.Status -eq 403) "(HTTP $($r.Status))"

$hp = $fields.Clone(); $hp['_hp'] = 'bot'; $hp['email'] = "smoke-hp-$rand@example.com"
$r = Invoke-Api POST '/api/leads' (New-Lead $TenantId $hp)
Check 'SPAM' 'honeypot submission is silently accepted (202)' ($r.Status -eq 202) "(HTTP $($r.Status))"

if ($leadId) {
    $r = Invoke-Api PUT "/api/admin/tenants/$TenantId/leads/$leadId/classification" @{ status = 'pending' } -Admin
    Check 'CLE-API' 'classification write-back accepted' ($r.Status -eq 200) "(HTTP $($r.Status) $($r.Raw))"
    $r = Invoke-Api POST "/api/admin/tenants/$TenantId/leads/$leadId/drafts" @{ channel = 'email'; subject = 'Smoke test'; body = 'Smoke test draft, please ignore.'; source = 'template' } -Admin
    Check 'DFU-API' 'draft stored as awaiting-approval' ($r.Status -eq 201 -and $r.Body.status -eq 'awaiting-approval') "(HTTP $($r.Status) $($r.Raw))"

    if ($SkipNotification) {
        Write-Host 'SKIP  NBO-01   event delivery check (-SkipNotification)'
    } else {
        $deadline = (Get-Date).AddSeconds($NotificationTimeoutSeconds)
        $status = $null
        do {
            $list = Invoke-Api GET "/api/admin/tenants/$TenantId/leads?limit=200" -Admin
            $item = @($list.Body.items) | Where-Object { $_.leadId -eq $leadId } | Select-Object -First 1
            $status = if ($item) { $item.notificationStatus } else { $null }
            if ($status -eq 'SENT' -or $status -eq 'FAILED') { break }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $deadline)
        Check 'NBO-01' 'lead.created event delivered to webhook (SENT)' ($status -eq 'SENT') "(status=$status; is LEAD_EVENTS_WEBHOOK_URL set and the n8n workflow active?)"
    }
}

$client.Dispose()
if ($script:failures -gt 0) {
    Write-Host "FAIL  $($script:failures) check(s) failed"
    exit 1
}
Write-Host 'PASS  all smoke checks passed'
exit 0
