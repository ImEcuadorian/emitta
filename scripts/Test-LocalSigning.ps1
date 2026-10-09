[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    # Run this script INSIDE Doppler so its injected flags are overridden in this process only.
    $env:EMITTA_SIGNING_ENABLED = 'false'
    $env:EMITTA_ARTIFACT_STORAGE_ENABLED = 'false'
    $env:EMITTA_SRI_RECEPTION_ENABLED = 'false'
    $env:EMITTA_SRI_AUTHORIZATION_ENABLED = 'false'
    $env:EMITTA_ARTIFACT_STORAGE_BUCKET = 'emitta-signing-e2e-' + [Guid]::NewGuid().ToString('N')
    & .\gradlew.bat compileJava test --tests '*auth*' --tests '*invoice*' --tests '*tenant*' --tests '*taxpayer*' --tests '*establishment*' --tests '*pointofissue*' --tests '*document.application*' --tests '*sriauthorization*' --tests '*fiscalsigning*' --tests '*fiscalprocessing*' --tests '*invoicexml*' --tests '*ProviderSubmission*Test' --tests '*SubmitFiscalDocumentToSriServiceTest' --tests '*PostgreSqlSriReceptionAttemptAdapterIntegrationTest' --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Compilation/unit/database tests failed' }
    & .\gradlew.bat objectStorageIntegrationTest --tests '*FiscalProcessingArtifactEndToEndIntegrationTest' --tests '*InvoiceXmlArtifactIntegrationTest' --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Signed artifact E2E failed' }
    Write-Output ('Tests passed. Dedicated MinIO test bucket: ' + $env:EMITTA_ARTIFACT_STORAGE_BUCKET)
} finally {
    Pop-Location
}
