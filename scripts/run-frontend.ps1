<#
    Serves the static frontend on http://localhost:5500

    There is no npm and no bundler: the frontend is plain HTML, CSS and
    JavaScript, so any static file server is enough. Python ships with Windows,
    and its http.server module is used here. The backend must be running
    separately on port 8080.
#>

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $root "frontend"

if (-not (Test-Path -LiteralPath $frontend)) {
    throw "Frontend folder not found: $frontend"
}

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

Set-Location -LiteralPath $frontend

Write-Host ""
Write-Host "  Skyline CRM - frontend" -ForegroundColor Cyan
Write-Host "  http://localhost:5500" -ForegroundColor DarkCyan
Write-Host "  API expected at http://localhost:8080/api" -ForegroundColor DarkGray
Write-Host ""

if ($python) {
    & $python -m http.server 5500
} else {
    & $node (Join-Path $root "scripts\static-server.js") $frontend 5500
}
