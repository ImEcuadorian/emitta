[CmdletBinding()]
param(
    [ValidateSet('normal', 'sustained', 'spike', 'academic-sustained', 'academic-spike')][string]$Profile = 'normal',
    [ValidateSet(20, 50, 100, 200, 500)][int]$Vus = 20,
    [ValidatePattern('^[1-9][0-9]*(s|m)$')][string]$Duration = '30s',
    [switch]$DimensionedSpike,
    [ValidateSet(1,2)][int]$AdmissionConcurrency = 1
)
$ErrorActionPreference = 'Stop'
if ($Profile -eq 'sustained' -and -not $PSBoundParameters.ContainsKey('Duration')) { $Duration = '10m' }
if ($Profile -eq 'academic-sustained' -and -not $PSBoundParameters.ContainsKey('Duration')) { $Duration = '5m' }
if ($Profile -eq 'academic-spike' -and -not $PSBoundParameters.ContainsKey('Duration')) { $Duration = '1m' }
$seconds = [int]$Duration.Substring(0, $Duration.Length - 1)
if ($Duration.EndsWith('m')) { $seconds *= 60 }
if ($seconds -gt 600) { throw 'Maximum controlled duration is 10m; longer tests require a token renewal design' }
if ($Profile -eq 'normal' -and $Vus -notin @(20, 50, 100)) { throw 'Normal: 20, 50 or 100 VUs' }
if ($Profile -in @('sustained','academic-sustained') -and $Vus -notin @(100, 200)) { throw 'Sustained: 100 or 200 VUs' }
if ($Profile -in @('spike','academic-spike') -and (-not $DimensionedSpike -or $Vus -ne 500)) { throw 'Spike requires 500 VUs and -DimensionedSpike after resource review' }
$settings = @{ EMITTA_LOAD_PROFILE = $Profile; EMITTA_LOAD_VUS = "$Vus"; EMITTA_LOAD_DURATION = $Duration; EMITTA_LOAD_SPIKE_DIMENSIONED = "$($DimensionedSpike.IsPresent)".ToLowerInvariant() }
$settings.EMITTA_LOAD_ADMISSION_CONCURRENCY = "$AdmissionConcurrency"
$saved = @{}
foreach ($key in $settings.Keys) { $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process'); [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process') }
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    # This task always provisions disposable services. It does not accept a target URL or private DB settings.
    & .\gradlew.bat operationalLoadTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Operational load thresholds or persistence invariants failed; review build/operational/load' }
} finally {
    Pop-Location
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
}
