[CmdletBinding(DefaultParameterSetName='Job')]
param(
    [Parameter(Mandatory)][Guid]$TenantId,
    [Parameter(Mandatory,ParameterSetName='Job')][Guid]$JobId,
    [Parameter(Mandatory,ParameterSetName='Admission')][Guid]$DocumentId,
    [Parameter(Mandatory,ParameterSetName='Admission')][Guid]$EventId
)
$ErrorActionPreference='Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $confirmation=Read-Host 'Type RECOVER to schedule only this reviewed local job or original event'
    if ($confirmation -cne 'RECOVER') { throw 'Recovery cancelled' }
    $target=if($PSCmdlet.ParameterSetName -eq 'Admission'){"--emitta.recovery.document-id=$DocumentId --emitta.recovery.event-id=$EventId"}else{"--emitta.recovery.job-id=$JobId"}
    $bootArguments="--spring.main.web-application-type=none --spring.profiles.active=processing-recovery --emitta.fiscal.worker.enabled=false --emitta.outbox.publisher.enabled=false --emitta.sri.reception.enabled=false --emitta.sri.authorization.enabled=false --emitta.processing.jobs.enabled=false --emitta.signing.enabled=false --emitta.ride.enabled=false --emitta.artifacts.storage.enabled=false --emitta.recovery.tenant-id=$TenantId $target"
    & .\gradlew.bat bootRun "--args=$bootArguments" --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Job recovery failed' }
} finally { Pop-Location }
