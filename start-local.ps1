$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$localDir = Join-Path $PSScriptRoot '.local'
New-Item -ItemType Directory -Force $localDir | Out-Null
$java = 'C:\Program Files\Java\jdk-18\bin\java.exe'
$credentials = Join-Path $localDir 'credentials.json'
if (!(Test-Path $credentials)) { throw 'Local credentials are missing: .local/credentials.json' }
$settings = Get-Content $credentials -Raw | ConvertFrom-Json
$env:LOCAL_DB_PASSWORD = $settings.DB_PASSWORD
$env:ADMIN_PASSWORD = $settings.ADMIN_PASSWORD
$env:RECEPTION_PASSWORD = $settings.RECEPTION_PASSWORD
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$data = Join-Path $localDir 'postgres'
& "$pgBin\pg_ctl.exe" -D $data status *> $null
if ($LASTEXITCODE -ne 0) {
    & "$pgBin\pg_ctl.exe" -D $data -l (Join-Path $localDir 'postgres.log') -o '-p 5433 -h 127.0.0.1' -w start
    if ($LASTEXITCODE -ne 0) { throw 'Local PostgreSQL startup failed. See .local/postgres.log.' }
}
$env:PORT = '8080'
$env:SPRING_PROFILES_ACTIVE = 'local'
try {
    $health = Invoke-RestMethod 'http://localhost:8080/health' -TimeoutSec 3
    if ($health.status -eq 'UP') { Write-Host 'App already running: http://localhost:8080'; exit }
} catch {}
$jar = Join-Path $PSScriptRoot 'target\salon-workforce-1.0.0.jar'
if (!(Test-Path $jar)) { throw 'Build the application first with mvn package.' }
$app = Start-Process -FilePath $java -ArgumentList @('-Duser.timezone=UTC', '-jar', ('"' + $jar + '"'), '--server.address=0.0.0.0') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $localDir 'app.log') -RedirectStandardError (Join-Path $localDir 'app-error.log') -PassThru
$app.Id | Set-Content (Join-Path $localDir 'app.pid')
Write-Host 'Starting http://localhost:8080. Logs and credentials are in .local.'
