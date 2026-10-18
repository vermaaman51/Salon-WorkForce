$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
foreach ($setting in @('DB_PASSWORD', 'ADMIN_PASSWORD', 'RECEPTION_PASSWORD')) {
    if (-not [Environment]::GetEnvironmentVariable($setting)) {
        $secretValue = Read-Host "Enter $setting (account passwords require 12+ characters)" -AsSecureString
        $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secretValue)
        try { [Environment]::SetEnvironmentVariable($setting, [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer), 'Process') }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer) }
    }
}
Write-Host 'Ensure PostgreSQL is running. Opening Spring Boot on http://localhost:8080'
mvn spring-boot:run
