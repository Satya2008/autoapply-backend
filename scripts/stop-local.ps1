# Stops the services started by scripts/start-local.ps1.

$names = 'core-api', 'job-service', 'matching-service', 'gateway'
$running = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
	Where-Object { $cmd = $_.CommandLine; $names | Where-Object { $cmd -match "$_-0\.0\.1-SNAPSHOT\.jar" } }

if (-not $running) {
	Write-Host 'Nothing to stop.'
	return
}
foreach ($process in $running) {
	Stop-Process -Id $process.ProcessId -Force
	Write-Host "Stopped pid $($process.ProcessId)"
}
