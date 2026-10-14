[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
# Testcontainers and the existing disposable MinIO helper use synthetic credentials.
# No Doppler configuration, private bucket or administrative server access is required.
$variables = @{
    RUN_SRI_LIVE_TEST = 'false'
    EMITTA_SRI_RECEPTION_ENABLED = 'false'
    EMITTA_SRI_AUTHORIZATION_ENABLED = 'false'
    EMITTA_FISCAL_WORKER_ENABLED = 'false'
    EMITTA_OUTBOX_PUBLISHER_ENABLED = 'false'
    EMITTA_PROCESSING_JOBS_ENABLED = 'false'
    EMITTA_RIDE_ENABLED = 'false'
    EMITTA_SIGNING_ENABLED = 'false'
    EMITTA_ARTIFACT_STORAGE_ENABLED = 'false'
}
$previous = @{}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    foreach ($name in $variables.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $variables[$name], 'Process')
    }
    & .\gradlew.bat build --rerun-tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build or regression tests failed' }
    & .\scripts\Test-IsolatedStorage.ps1
    Write-Host 'Validation completed using disposable services and synthetic data; no SRI transmission.'
} finally {
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    Pop-Location
}
