<#
  Loads the realistic demonstration records from database/sample_data.sql.
  The SQL file is idempotent and does not delete existing records.
#>

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
if (-not (Test-Path -LiteralPath $mysql)) {
    $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
    if ($mysqlCommand) { $mysql = $mysqlCommand.Source }
}
if (-not (Test-Path -LiteralPath $mysql)) {
    throw 'MySQL client was not found. Install MySQL or add mysql.exe to PATH.'
}

Write-Host 'Loading Skyline CRM sample data into MySQL skyline_crm at localhost:3307...' -ForegroundColor Cyan
Get-Content -Raw (Join-Path $root 'database\sample_data.sql') |
    & $mysql -h 127.0.0.1 -P 3307 -u root -p

if ($LASTEXITCODE -ne 0) {
    throw "Sample-data import failed with exit code $LASTEXITCODE."
}

Write-Host 'Sample-data import completed.' -ForegroundColor Green
