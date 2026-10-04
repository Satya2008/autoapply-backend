# Starts a single-node Kafka (KRaft, no ZooKeeper, no Docker) with a small heap.
# Set KAFKA_HOME to the unpacked Kafka folder; the first run formats the storage.
#
#   $env:KAFKA_HOME = "$env:USERPROFILE\tools\kafka_2.13-4.3.1"
#   .\scripts\start-kafka.ps1

$ErrorActionPreference = 'Stop'
if (-not $env:KAFKA_HOME) { throw 'Set KAFKA_HOME to the Kafka folder first.' }
$kafka = $env:KAFKA_HOME
$data = Join-Path (Split-Path $kafka) 'kafka-data'
$logs = Join-Path (Split-Path $kafka) 'kafka-logs'
$config = Join-Path $kafka 'config\naukriradar.properties'
$classpath = Join-Path $kafka 'libs\*'
New-Item -ItemType Directory -Force $logs | Out-Null

if (-not (Test-Path $config)) {
	$lines = Get-Content (Join-Path $kafka 'config\server.properties')
	$lines = $lines -replace '^log.dirs=.*', ('log.dirs=' + ($data -replace '\', '/'))
	[IO.File]::WriteAllLines($config, $lines)
}
if (-not (Test-Path (Join-Path $data 'meta.properties'))) {
	# the .bat scripts overflow the Windows command line with their classpath, so call Java directly
	$id = (& java -cp $classpath kafka.tools.StorageTool random-uuid).Trim()
	& java -cp $classpath kafka.tools.StorageTool format --standalone -t $id -c $config | Out-Null
}

Start-Process java -ArgumentList '-Xmx256m', '-Xms128m', '-XX:+UseSerialGC', '-cp', "`"$classpath`"",
	"-Dlog4j2.configurationFile=$kafka\config\log4j2.yaml", "-Dkafka.logs.dir=$logs", 'kafka.Kafka', $config `
	-WindowStyle Hidden -RedirectStandardOutput "$logs\out.log" -RedirectStandardError "$logs\err.log"
Write-Host "Kafka starting on localhost:9092 (logs in $logs)"
