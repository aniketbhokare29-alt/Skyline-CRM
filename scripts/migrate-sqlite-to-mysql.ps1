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
    $maven = "C:\apache-maven-3.9.16\bin\mvn.cmd"
}
if (-not (Test-Path -LiteralPath $maven)) {
    throw "Maven was not found. Install Maven or add it to PATH."
}

if (-not (Test-Path -LiteralPath (Join-Path $root "crm.db"))) {
    throw "crm.db was not found at the project root."
}

Write-Host "The existing crm.db will be read only; it will not be deleted or changed." -ForegroundColor Yellow
$mavenRepo = Join-Path $root ".m2"
$sharedRepo = Join-Path (Split-Path -Parent $root) ".tools\m2"
if (Test-Path -LiteralPath $sharedRepo) { $mavenRepo = $sharedRepo }
& $maven "-Dmaven.repo.local=$mavenRepo" "-Dsqlite.db=$root\crm.db" `
    "-Dexec.mainClass=com.saivandan.crm.SqliteToMysqlMigration" `
    "-Dexec.classpathScope=runtime" -f "$root\backend\pom.xml" clean compile `
    "org.codehaus.mojo:exec-maven-plugin:3.1.1:java"
