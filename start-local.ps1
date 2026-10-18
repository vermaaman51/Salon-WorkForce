$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$localDir = Join-Path $PSScriptRoot '.local'
New-Item -ItemType Directory -Force $localDir | Out-Null
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$java = 'C:\Program Files\Java\jdk-18\bin\java.exe'
$credentials = Join-Path $localDir 'credentials.json'
if (!(Test-Path $credentials)) {
    @{
        DB_PASSWORD = [Guid]::NewGuid().ToString('N')
        ADMIN_PASSWORD = 'Admin-' + [Guid]::NewGuid().ToString('N').Substring(0,16)
        RECEPTION_PASSWORD = 'Reception-' + [Guid]::NewGuid().ToString('N').Substring(0,16)
    } | ConvertTo-Json | Set-Content $credentials
}
$settings = Get-Content $credentials -Raw | ConvertFrom-Json
$env:DB_PASSWORD = $settings.DB_PASSWORD
$env:ADMIN_PASSWORD = $settings.ADMIN_PASSWORD
$env:RECEPTION_PASSWORD = $settings.RECEPTION_PASSWORD
$env:DB_USER = 'salon'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/salon'
$env:PORT = '8080'
$env:SPRING_PROFILES_ACTIVE = 'default'
$data = Join-Path $localDir 'postgres'
if (!(Test-Path (Join-Path $data 'PG_VERSION'))) {
    $pwFile = Join-Path $localDir 'pg-password.tmp'
    try {
        $env:DB_PASSWORD | Set-Content $pwFile -Encoding ascii
        & "$pgBin\initdb.exe" -D $data -U salon --pwfile=$pwFile --auth=scram-sha-256 --encoding=UTF8 --locale=C
        if ($LASTEXITCODE -ne 0) { throw 'Database initialization failed.' }
    } finally { Remove-Item $pwFile -ErrorAction SilentlyContinue }
}
& "$pgBin\pg_ctl.exe" -D $data status *> $null
if ($LASTEXITCODE -ne 0) {
    & "$pgBin\pg_ctl.exe" -D $data -l (Join-Path $localDir 'postgres.log') -o '-p 5433 -h 127.0.0.1' -w start
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL startup failed. See .local/postgres.log.' }
}
$env:PGPASSWORD = $env:DB_PASSWORD
try {
    $exists = & "$pgBin\psql.exe" -h 127.0.0.1 -p 5433 -U salon -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='salon'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot connect to PostgreSQL.' }
    if ($exists -ne '1') {
        & "$pgBin\createdb.exe" -h 127.0.0.1 -p 5433 -U salon salon
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create salon database.' }
    }
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
try {
    $health = Invoke-RestMethod 'http://localhost:8080/health' -TimeoutSec 3
    if ($health.status -eq 'UP') { Write-Host 'App already running: http://localhost:8080'; exit }
} catch {}
$jar = Join-Path $PSScriptRoot 'target\salon-workforce-1.0.0.jar'
if (!(Test-Path $jar)) { throw 'Build the application first with mvn package.' }
$app = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar + '"'), '--server.address=0.0.0.0') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $localDir 'app.log') -RedirectStandardError (Join-Path $localDir 'app-error.log') -PassThru
$app.Id | Set-Content (Join-Path $localDir 'app.pid')
Write-Host 'Starting http://localhost:8080. Logs and credentials are in .local.'


