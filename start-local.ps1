$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$localDir = Join-Path $PSScriptRoot '.local'
New-Item -ItemType Directory -Force $localDir | Out-Null
$java = 'C:\Program Files\Java\jdk-18\bin\java.exe'
$railwayFile = Join-Path $localDir 'railway.json'
if (!(Test-Path $railwayFile)) { throw 'Configure .local/railway.json with Railway public JDBC DB_URL, DB_USER and DB_PASSWORD.' }
$railway = Get-Content $railwayFile -Raw | ConvertFrom-Json
if (!$railway.DB_URL -or $railway.DB_URL -notmatch '^jdbc:postgresql://' -or $railway.DB_URL -match '\.railway\.internal') {
    throw 'Set DB_URL in .local/railway.json to jdbc:postgresql://PUBLIC_HOST:PUBLIC_PORT/railway using Railway Public Networking.'
}
$env:DB_URL = $railway.DB_URL
$env:DB_USER = $railway.DB_USER
$env:DB_PASSWORD = $railway.DB_PASSWORD
$credentials = Join-Path $localDir 'credentials.json'
if (Test-Path $credentials) {
    $settings = Get-Content $credentials -Raw | ConvertFrom-Json
    $env:ADMIN_PASSWORD = $settings.ADMIN_PASSWORD
    $env:RECEPTION_PASSWORD = $settings.RECEPTION_PASSWORD
}
$env:PORT = '8080'
$env:SPRING_PROFILES_ACTIVE = 'default'
try {
    $health = Invoke-RestMethod 'http://localhost:8080/health' -TimeoutSec 3
    if ($health.status -eq 'UP') { Write-Host 'App already running: http://localhost:8080'; exit }
} catch {}
$jar = Join-Path $PSScriptRoot 'target\salon-workforce-1.0.0.jar'
if (!(Test-Path $jar)) { throw 'Build the application first with mvn package.' }
$app = Start-Process -FilePath $java -ArgumentList @('-Duser.timezone=UTC', '-jar', ('"' + $jar + '"'), '--server.address=0.0.0.0') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $localDir 'app.log') -RedirectStandardError (Join-Path $localDir 'app-error.log') -PassThru
$app.Id | Set-Content (Join-Path $localDir 'app.pid')
Write-Host 'Starting http://localhost:8080. Logs and credentials are in .local.'


