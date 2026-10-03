<#
    Starts the Skyline CRM JSON API on http://localhost:8080/api

    The backend is a plain Java HttpServer, so the only build tool involved is
    Maven for compiling and launching. It is always started from the repository
    root so Maven and the project configuration resolve consistently.
#>

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

if ([string]::IsNullOrWhiteSpace($env:CRM_DB_PASSWORD)) {
    $securePassword = Read-Host "Enter MySQL password for root" -AsSecureString
    $passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $env:CRM_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
    }
}

$maven = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $maven) {
    foreach ($candidate in @("C:\apache-maven-3.9.16\bin\mvn.cmd", "$env:MAVEN_HOME\bin\mvn.cmd")) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { $maven = $candidate; break }
    }
}
if (-not $maven) {
    throw "Maven was not found. Install Maven or add it to PATH."
}

$mavenRepo = Join-Path $root ".m2"
$sharedRepo = Join-Path (Split-Path -Parent $root) ".tools\m2"
if (Test-Path -LiteralPath $sharedRepo) { $mavenRepo = $sharedRepo }

Write-Host ""
Write-Host "  Skyline CRM - JSON API" -ForegroundColor Cyan
Write-Host "  http://localhost:8080/api" -ForegroundColor DarkCyan
Write-Host "  database: MySQL skyline_crm at localhost:3307" -ForegroundColor DarkGray
Write-Host ""

& $maven "-Dmaven.repo.local=$mavenRepo" -f "$root\backend\pom.xml" clean compile exec:java
