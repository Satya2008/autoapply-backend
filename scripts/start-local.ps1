# Starts every service on this machine: builds the jars, checks MySQL and Redis, then runs
# the services and the gateway in the background.
# Logs go to logs/<service>.log. Stop them with scripts/stop-local.ps1.
#
#   .\scripts\start-local.ps1            # build, then start
#   .\scripts\start-local.ps1 -SkipBuild # start the jars already built

param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$services = [ordered]@{
	'core-api'         = 8081
	'job-service'      = 8082
	'matching-service' = 8083
	'apply-worker'     = 8084
	'notification-service' = 8085
	'gateway'          = 8080
}

function Test-Port([int]$port) {
	$client = New-Object System.Net.Sockets.TcpClient
	try {
		$client.Connect('localhost', $port)
		return $true
	}
	catch {
		return $false
	}
	finally {
		$client.Close()
	}
}

if (-not (Test-Port 3306)) { throw 'MySQL is not running on localhost:3306.' }
if (-not (Test-Port 6379)) { throw 'Redis is not running on localhost:6379.' }
if (-not (Test-Port 9092)) { Write-Warning 'Kafka is not running on localhost:9092: events wait in the outbox until it is (scripts\start-kafka.ps1).' }

$busy = $services.GetEnumerator() | Where-Object { Test-Port $_.Value }
if ($busy) {
	throw "Already in use: $(($busy | ForEach-Object { "$($_.Key) :$($_.Value)" }) -join ', '). Run scripts\stop-local.ps1 first."
}

if (-not $SkipBuild) {
	Write-Host 'Building jars...'
	& .\gradlew.bat bootJar -q
	if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
}

New-Item -ItemType Directory -Force logs | Out-Null
foreach ($name in $services.Keys) {
	$jar = "$name\build\libs\$name-0.0.1-SNAPSHOT.jar"
	if (-not (Test-Path $jar)) { throw "$jar not found; run without -SkipBuild." }
	Start-Process java -ArgumentList '-Xmx256m', '-XX:+UseSerialGC', '-jar', $jar `
		-RedirectStandardOutput "logs\$name.log" -RedirectStandardError "logs\$name.err.log" -WindowStyle Hidden
	Write-Host "Started $name"
}

Write-Host 'Waiting for health checks...'
$deadline = (Get-Date).AddMinutes(6)
foreach ($entry in $services.GetEnumerator()) {
	$up = $false
	while (-not $up -and (Get-Date) -lt $deadline) {
		try {
			$up = (Invoke-WebRequest "http://localhost:$($entry.Value)/actuator/health" -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200
		}
		catch {
			Start-Sleep -Seconds 2
		}
	}
	if (-not $up) { throw "$($entry.Key) did not come up; see logs\$($entry.Key).log" }
	Write-Host "  $($entry.Key) is up on :$($entry.Value)"
}

Write-Host ''
Write-Host 'Swagger UI: http://localhost:8080/swagger-ui.html'
Write-Host 'Try the whole flow: python scripts\smoke-test.py'
