[CmdletBinding()]
param([string]$Image = 'dhi.io/minio:0.20251015.172955', [string]$TestFilter = '')
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$container = 'emitta-isolated-minio-' + [Guid]::NewGuid().ToString('N')
$access = 'synthetic-' + [Guid]::NewGuid().ToString('N')
$secret = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$changes = @{
    RUN_SRI_LIVE_TEST = 'false'
    EMITTA_SRI_RECEPTION_ENABLED = 'false'
    EMITTA_SRI_AUTHORIZATION_ENABLED = 'false'
    EMITTA_FISCAL_WORKER_ENABLED = 'false'
    EMITTA_PROCESSING_JOBS_ENABLED = 'false'
    EMITTA_OUTBOX_PUBLISHER_ENABLED = 'false'
    EMITTA_RIDE_ENABLED = 'false'
    EMITTA_SIGNING_ENABLED = 'false'
    EMITTA_ARTIFACT_STORAGE_ENABLED = 'false'
    EMITTA_ARTIFACT_STORAGE_REGION = 'us-east-1'
    EMITTA_ARTIFACT_STORAGE_BUCKET = 'isolated-' + [Guid]::NewGuid().ToString('N')
    EMITTA_ARTIFACT_STORAGE_ACCESS_KEY = $access
    EMITTA_ARTIFACT_STORAGE_SECRET_KEY = $secret
    EMITTA_ARTIFACT_STORAGE_PATH_STYLE = 'true'
    EMITTA_ARTIFACT_STORAGE_ENDPOINT = ''
}
$saved = @{}
foreach ($key in $changes.Keys) { $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
Push-Location $repository
try {
    & docker run --detach --name $container --label 'io.emitta.scope=isolated-test' --publish '127.0.0.1::9000' --env "MINIO_ROOT_USER=$access" --env "MINIO_ROOT_PASSWORD=$secret" $Image server /data --console-address ':9001' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start disposable MinIO' }
    $mapping = (& docker port $container '9000/tcp').Trim()
    if ($mapping -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected MinIO port binding' }
    $changes.EMITTA_ARTIFACT_STORAGE_ENDPOINT = 'http://' + $mapping
    foreach ($key in $changes.Keys) { [Environment]::SetEnvironmentVariable($key, $changes[$key], 'Process') }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try { Invoke-WebRequest ($changes.EMITTA_ARTIFACT_STORAGE_ENDPOINT + '/minio/health/ready') -UseBasicParsing -TimeoutSec 1 | Out-Null; $ready = $true; break }
        catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $ready) { throw 'Disposable MinIO did not become ready' }
    $testArguments = @('objectStorageIntegrationTest', '--console=plain')
    if ($TestFilter) { $testArguments += @('--tests', $TestFilter) }
    & .\gradlew.bat @testArguments
    if ($LASTEXITCODE -ne 0) { throw 'Isolated storage tests failed' }
} finally {
    foreach ($key in $saved.Keys) {
        if ($null -eq $saved[$key]) {
            # PowerShell/.NET can coerce null to an empty string. Missing flags must remain missing.
            Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process')
        }
    }
    # Target is the unique container created above. Never stop/remove the pilot services.
    $exists = & docker ps --all --quiet --filter "name=^/$container$"
    if ($exists) {
        $labels = (& docker inspect --format '{{ json .Config.Labels }}' $container | ConvertFrom-Json)
        if ($LASTEXITCODE -eq 0 -and $labels.'io.emitta.scope' -eq 'isolated-test') { & docker rm --force --volumes $container | Out-Null }
    }
    Pop-Location
}
