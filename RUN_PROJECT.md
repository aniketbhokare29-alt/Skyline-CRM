# Skyline CRM: Run in VS Code

## 1. Open the project

Open VS Code and open this folder:

```text
C:\Users\anike\Documents\ChatGPT\FINAL
```

Open the VS Code terminal with **Terminal > New Terminal**.

## 2. Go to the project folder

Run:

```powershell
cd C:\Users\anike\Documents\ChatGPT\FINAL
```

## 3. Set the MySQL password

Use your actual MySQL root password:

```powershell
$env:CRM_DB_PASSWORD = "root"
```

Replace `root` if your MySQL password is different. Set this value again
whenever you open a new VS Code terminal.

## 4. Check MySQL

```powershell
Get-Service MySQL80
```

The service should show `Running`.

## 5. First-time database setup

Run the schema once:

```powershell
Get-Content .\database\mysql_schema.sql |
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -h 127.0.0.1 -P 3307 -u root -p
 
```

Enter your MySQL password when prompted.

Apply the role migration:

```powershell
Get-Content .\database\role_migration.sql |
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -h 127.0.0.1 -P 3307 -u root -p
```

Add Booking and Sales IDs:

```powershell
Get-Content .\database\business_crud_migration.sql |
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -h 127.0.0.1 -P 3307 -u root -p
```

## 6. Copy old SQLite records

Run this only if the old `crm.db` records have not already been migrated:

```powershell
.\scripts\migrate-sqlite-to-mysql.ps1
```

Do not run this repeatedly, because it can create duplicate business records.

## 7. Start the project

In the same terminal where `CRM_DB_PASSWORD` was set, run:

```powershell
.\scripts\run.ps1
```

Keep this terminal open. The project starts:

```text
Frontend: http://localhost:5500
Backend:  http://localhost:8080/api
Database: MySQL skyline_crm
```

## 8. Open the CRM

Open this address in Chrome:

```text
http://localhost:5500
```

## 9. Check the backend connection

Open a second VS Code terminal and run:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

The `db` value should start with:

```text
jdbc:mysql://localhost:3307/skyline_crm
```

## 10. Test accounts

```text
Admin Owner: admin@skyline.local / admin123
Employee:    employee@skyline.local / employee123
Agent:       agent@skyline.local / agent123
```

## 11. Stop the project

Return to the terminal running `run.ps1` and press:

```text
Ctrl + C
```

## 12. Fix a port-in-use error

If port `8080` or `5500` is already in use, run:

```powershell
Get-NetTCPConnection -LocalPort 8080,5500 -State Listen -ErrorAction SilentlyContinue |
ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

Then start again:

```powershell
$env:CRM_DB_PASSWORD = "root"
.\scripts\run.ps1
```

## 13. Check backend errors

If the backend stops, run:

```powershell
Get-Content .\api.err.log -Tail 50
Get-Content .\api.log -Tail 50
```
