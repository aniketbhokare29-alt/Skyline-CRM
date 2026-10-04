<#
    One-command launcher for the separated Skyline CRM.

        backend  : Java HttpServer JSON API on http://localhost:8080/api
        frontend : static HTML/CSS/JS site on http://localhost:5500

    Both are started in the background from this script. Closing this window,
    or pressing Ctrl+C, stops the API and the static file server.

    To run only one side, use scripts\run-backend.ps1 or scripts\run-frontend.ps1.
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

$pythonCandidates = @(
    (Get-Command python -ErrorAction SilentlyContinue).Source,
    (Get-Command py -ErrorAction SilentlyContinue).Source,
    "$env:LocalAppData\Programs\Python\Python310\python.exe",
    "$env:LocalAppData\Programs\Python\Python311\python.exe",
    "$env:LocalAppData\Programs\Python\Python312\python.exe",
    "$env:LocalAppData\Programs\Python\Python313\python.exe"
)
$python = $pythonCandidates |
    Where-Object { $_ -and (Test-Path -LiteralPath $_) } |
    Select-Object -First 1
$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $python -and -not $node) { throw "Python or Node.js is required to serve the frontend." }

$apiLog = Join-Path $root "api.log"
$apiErrLog = Join-Path $root "api.err.log"

# cmd.exe is used because mvn.cmd is a batch file. The paths contain no spaces,
# so no nested quoting is needed.
$backendArgs = @(
    "/c", $maven,
    "-Dmaven.repo.local=$mavenRepo",
    "-f", "$root\backend\pom.xml",
    "clean", "compile", "exec:java"
)

$backend = $null
$frontend = $null
$apiConnections = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
$apiAlreadyRunning = $apiConnections.Count -gt 0
$frontendAlreadyRunning = @(Get-NetTCPConnection -LocalPort 5500 -State Listen -ErrorAction SilentlyContinue).Count -gt 0

# Reuse only the current Skyline API. A previous build can still occupy 8080
# and otherwise makes the browser talk to stale routes and database code.
if ($apiAlreadyRunning) {
    $apiIsCurrent = $false
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 2
        $apiIsCurrent = $health.service -eq "skyline-crm-api" `
            -and $health.version -eq "6" `
            -and [string]$health.db -like "jdbc:mysql:*"
    } catch {
        $apiIsCurrent = $false
    }
    if (-not $apiIsCurrent) {
        Write-Host "  Replacing stale API on port 8080..." -ForegroundColor Yellow
        $apiConnections | ForEach-Object {
            Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Milliseconds 500
        $apiAlreadyRunning = $false
    }
}

try {
    if ($apiAlreadyRunning) {
        Write-Host "  API        already running at http://localhost:8080/api" -ForegroundColor Yellow
    } else {
        $backend = Start-Process -FilePath "cmd.exe" -ArgumentList $backendArgs `
            -WorkingDirectory $root -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $apiLog -RedirectStandardError $apiErrLog
    }

    if ($frontendAlreadyRunning) {
        Write-Host "  Frontend   already running at http://localhost:5500" -ForegroundColor Yellow
    } else {
        if ($python) {
            $frontend = Start-Process -FilePath $python -ArgumentList "-m", "http.server", "5500" `
                -WorkingDirectory (Join-Path $root "frontend") -WindowStyle Hidden -PassThru `
                -RedirectStandardOutput (Join-Path $root "frontend.log") -RedirectStandardError (Join-Path $root "frontend.err.log")
        } else {
            $frontend = Start-Process -FilePath $node -ArgumentList (Join-Path $root "scripts\static-server.js"), (Join-Path $root "frontend"), "5500" `
                -WorkingDirectory $root -WindowStyle Hidden -PassThru `
                -RedirectStandardOutput (Join-Path $root "frontend.log") -RedirectStandardError (Join-Path $root "frontend.err.log")
        }
    }

    Write-Host ""
    Write-Host "  Skyline CRM is starting" -ForegroundColor Cyan
    Write-Host "  --------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "  Open       http://localhost:5500" -ForegroundColor Green
    Write-Host "  API        http://localhost:8080/api" -ForegroundColor Green
    Write-Host "  Database   MySQL skyline_crm at localhost:3307" -ForegroundColor DarkGray
    Write-Host "  Logs       api.log / api.err.log / frontend.log" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  Press Ctrl+C to stop both servers." -ForegroundColor Yellow
    Write-Host ""

    while (($null -eq $backend -or -not $backend.HasExited) -and ($null -eq $frontend -or -not $frontend.HasExited)) {
        Start-Sleep -Seconds 2
    }

    if ($backend -and $backend.HasExited) {
        Write-Host "The backend stopped. Check api.log and api.err.log." -ForegroundColor Red
    }
    if ($frontend -and $frontend.HasExited) {
        Write-Host "The frontend stopped. Check frontend.log and frontend.err.log." -ForegroundColor Red
    }
}
finally {
    foreach ($process in @($backend, $frontend)) {
        if ($process -and -not $process.HasExited) {
            # /T kills the whole tree: cmd.exe -> Maven JVM for the API, and
            # the python process for the static server.
            & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
        }
    }
}
