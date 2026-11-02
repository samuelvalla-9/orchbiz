<#
.SYNOPSIS
  Runs the OrchBiz CLI (tools/orchbiz-cli), building it with Maven first if needed.
.EXAMPLE
  .\tools\orchbiz.ps1 init-client apex-coaching
  .\tools\orchbiz.ps1 parse-intake apex-coaching
  .\tools\orchbiz.ps1 --help
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$project = Join-Path $PSScriptRoot 'orchbiz-cli'
$jar = Join-Path $project 'target\orchbiz-cli.jar'

$needsBuild = -not (Test-Path $jar)
if (-not $needsBuild) {
    $jarTime = (Get-Item $jar).LastWriteTimeUtc
    $newer = Get-ChildItem -Recurse -File (Join-Path $project 'src\main'), (Join-Path $project 'pom.xml') |
        Where-Object { $_.LastWriteTimeUtc -gt $jarTime } | Select-Object -First 1
    $needsBuild = [bool]$newer
}
if ($needsBuild) {
    Write-Host 'Building orchbiz-cli (first run or sources changed)...' -ForegroundColor DarkGray
    & mvn -q -B -f (Join-Path $project 'pom.xml') package -DskipTests | Out-Host
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $jar)) {
        Write-Error 'orchbiz-cli build failed. Run: mvn -f tools/orchbiz-cli/pom.xml package'
        exit 2
    }
}

Push-Location $root
try {
    # PowerShell parses unquoted a,b as an array; rejoin so picocli sees "a,b".
    $cliArgs = @(foreach ($a in $args) { if ($a -is [array]) { $a -join ',' } else { $a } })
    & java -jar $jar @cliArgs
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $code
