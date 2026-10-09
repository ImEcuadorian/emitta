[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$DestinationDirectory)
$ErrorActionPreference = 'Stop'
foreach ($name in @('DB_MIGRATION_USERNAME','DB_MIGRATION_PASSWORD','DB_NAME')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw ('Missing configuration: ' + $name + '. Run through Doppler.')
    }
}
$backupDirectory = [IO.Path]::GetFullPath($DestinationDirectory)
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$operatorIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $backupDirectory /inheritance:r /grant:r "${operatorIdentity}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot restrict backup directory permissions' }
$backupName = 'emitta-local-backup-' + [Guid]::NewGuid().ToString('N') + '.dump'
$containerPath = '/tmp/' + $backupName
$destination = Join-Path $backupDirectory $backupName
$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD')
try {
    $env:PGPASSWORD = $env:DB_MIGRATION_PASSWORD
    # PGPASSWORD is passed in the child environment; no password argument/log/transcript.
    & docker exec -e PGPASSWORD emitta-postgres pg_dump `
        -U $env:DB_MIGRATION_USERNAME -d $env:DB_NAME --format=custom --no-owner --no-acl --file=$containerPath
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }
    & docker exec emitta-postgres pg_restore --exit-on-error --file=/dev/null $containerPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Backup archive validation failed' }
    & docker cp "emitta-postgres:$containerPath" $destination | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy backup outside repository' }
    $backupStream = [IO.File]::OpenRead($destination)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $backupHash = [BitConverter]::ToString($sha.ComputeHash($backupStream)).Replace('-','').ToLowerInvariant() }
    finally { $backupStream.Dispose(); $sha.Dispose() }
    $manifest = [ordered]@{
        database = $env:DB_NAME
        schema = 'emitta'
        createdAt = [DateTimeOffset]::UtcNow.ToString('o')
        sha256 = $backupHash
    }
    $manifest | ConvertTo-Json | Set-Content -LiteralPath ($destination + '.json') -Encoding UTF8
    Write-Output $destination
} finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD',$previousPgPassword)
    if ($containerPath -notmatch '^/tmp/emitta-local-backup-[a-f0-9]{32}\.dump$') { throw 'Unexpected temporary path' }
    & docker exec emitta-postgres rm -f $containerPath | Out-Null
}
