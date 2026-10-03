# run.ps1 Explanation

> Last updated: the app runs as two processes. `scripts/run.ps1` now starts
> the JSON API (via `scripts/run-backend.ps1` logic) and the static frontend
> (`scripts/run-frontend.ps1`) together and stops both when the window closes.
> Open the frontend at `http://localhost:5500`; the API is on
> `http://localhost:8080/api`.

File:

`scripts/run.ps1`

## Purpose

This PowerShell script starts Skyline CRM on Windows.

## What it does

1. Stops the script when an error occurs.
2. Finds the project root folder.
3. Changes into the project root.
4. Finds Maven.
5. Uses the project-local `.m2` dependency cache.
6. Cleans, compiles, and runs the application.

## Run command

Open PowerShell in the project folder and run:

```powershell
.\scripts\run.ps1
```

Then open:

```text
http://localhost:8080
```

Press `Ctrl+C` in the terminal to stop the server.
