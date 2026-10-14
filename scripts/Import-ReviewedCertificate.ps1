param(
    [Parameter(Mandatory)][Uri] $ApiBase,
    [Parameter(Mandatory)][Guid] $TaxpayerId,
    [Parameter(Mandatory)][string] $CertificatePath,
    [Parameter(Mandatory)][string] $AuthorizationEvidencePath,
    [string] $Alias,
    [string] $OnboardingStatePath
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
if ($ApiBase.Scheme -ne 'https' -or $ApiBase.UserInfo -or $ApiBase.Query -or $ApiBase.Fragment) {
    throw 'Use an HTTPS API origin without credentials, query or fragment.'
}
$certificate = Get-Item -LiteralPath $CertificatePath
$evidence = Get-Item -LiteralPath $AuthorizationEvidencePath
if ($certificate.PSIsContainer -or $certificate.Length -gt 1MB -or $certificate.Length -eq 0 -or
    $evidence.PSIsContainer -or $evidence.Length -gt 256KB -or $evidence.Length -eq 0) {
    throw 'Certificate/evidence missing, empty or too large.'
}
if ((Read-Host 'Has the holder authority for this taxpayer been independently reviewed? Type REVIEWED') -cne 'REVIEWED') {
    throw 'Administrative review required.'
}
$token = $null
$password = Read-Host 'PKCS#12 password' -AsSecureString
$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(30)
$form = [System.Net.Http.MultipartFormDataContent]::new()
$response = $null
try {
    if ($OnboardingStatePath) {
        $state = Get-Content -LiteralPath $OnboardingStatePath -Raw | ConvertFrom-Json
        if ([Guid]$state.taxpayer.id -ne $TaxpayerId -or -not $state.onboardingClient.clientSecret) {
            throw 'Private onboarding state does not match this taxpayer.'
        }
        $authBody = @{
            clientId = $state.onboardingClient.clientId
            clientSecret = $state.onboardingClient.clientSecret
        } | ConvertTo-Json -Compress
        $authContent = [System.Net.Http.StringContent]::new($authBody, [Text.Encoding]::UTF8, 'application/json')
        $authResponse = $null
        try {
            $origin = $ApiBase.GetLeftPart([System.UriPartial]::Authority)
            $authResponse = $client.PostAsync("$origin/api/v1/auth/token", $authContent).GetAwaiter().GetResult()
            if (-not $authResponse.IsSuccessStatusCode) {
                throw "Authentication returned HTTP $([int]$authResponse.StatusCode)."
            }
            $auth = $authResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
            $token = ConvertTo-SecureString -String $auth.accessToken -AsPlainText -Force
        } finally {
            if ($authResponse) { $authResponse.Dispose() }
            $authContent.Dispose()
            $authBody = $null; $auth = $null; $state = $null
        }
    } else {
        $token = Read-Host 'Administrative JWT (certificates:write)' -AsSecureString
    }
    $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new(
        'Bearer', [System.Net.NetworkCredential]::new('', $token).Password)
    $form.Add([System.Net.Http.StreamContent]::new([IO.File]::OpenRead($certificate.FullName)), 'certificate', 'certificate.p12')
    $form.Add([System.Net.Http.StreamContent]::new([IO.File]::OpenRead($evidence.FullName)), 'authorizationEvidence', 'reviewed-evidence')
    $form.Add([System.Net.Http.StringContent]::new([System.Net.NetworkCredential]::new('', $password).Password), 'password')
    $form.Add([System.Net.Http.StringContent]::new('true'), 'authorizationReviewed')
    if ($Alias) { $form.Add([System.Net.Http.StringContent]::new($Alias), 'alias') }
    $origin = $ApiBase.GetLeftPart([System.UriPartial]::Authority)
    $response = $client.PostAsync("$origin/api/v1/taxpayers/$TaxpayerId/certificates", $form).GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "Certificate registration returned HTTP $([int]$response.StatusCode). No automatic retry performed."
    }
    $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json |
        Select-Object id, fingerprint, validUntil
} finally {
    if ($response) { $response.Dispose() }
    $form.Dispose(); $client.Dispose(); $handler.Dispose()
    if ($token) { $token.Dispose() }
    $password.Dispose()
}
