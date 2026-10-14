[CmdletBinding()]
param([ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9_.-]*$')][string]$Container='emitta-rabbitmq')
$ErrorActionPreference='Stop'
# Use stdin to preserve JSON quoting in Windows PowerShell 5 and PowerShell 7.
$policyScript=@'
set -eu
rabbitmqctl set_policy --apply-to queues emitta-fiscal-dlx '^emitta[.]fiscal[.]documents$' '{"dead-letter-exchange":"emitta.deadletters"}'
rabbitmqctl list_policies
'@
$start=New-Object System.Diagnostics.ProcessStartInfo
$start.FileName='docker'
$start.Arguments="exec -i $Container sh -s"
$start.UseShellExecute=$false
$start.CreateNoWindow=$true
$start.RedirectStandardInput=$true
$process=New-Object System.Diagnostics.Process
$process.StartInfo=$start
try {
    if (-not $process.Start()) { throw 'Docker could not start' }
    $process.StandardInput.Write($policyScript.Replace("`r`n","`n") + "`n")
    $process.StandardInput.Close()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'DLX policy installation failed' }
} finally { $process.Dispose() }
