# Skyline Real Estate CRM

Simple Java CRM for a real estate project, built phase by phase from the attached brief.

The project runs as two parts: a static HTML/CSS/JavaScript frontend and a Java
JSON API backed by MySQL. Both the UI and the persistence logic are unchanged
from the original single-server version.

## Current Status

Phase 1 through Phase 7 are implemented:

- Login page with role selection
- Password sign-in for every role
- Admin Owner-controlled user creation
- Role-aware dashboard and sidebar for ADMIN_OWNER, EMPLOYEE, and AGENT
- Client enquiry list and create form
- Lead qualification list and create form
- Property inventory list and create form
- Follow-up list and create form
- Site visit booking list and create form
- Negotiation list and create form
- Booking list and create form
- Customer document checklist
- Loan processing list and create form
- Agreement list and create form
- Customer payment tracking with paid and pending totals
- Possession checklist and handover status
- Customer support ticket tracking
- Employee master records
- Daily attendance tracking
- Leave management
- Salary processing with gross, deductions, net salary, payment mode, and status
- Vendor master, purchase orders, vendor bills, and vendor payments
- Petty cash entries and approval tracking
- Finance dashboard with collections, receivables, vendor outstanding, salary expense, petty cash, and profit/loss summary
- Simple, dependency-free Java HTTP server
- MySQL database storage with seed data for quick demos

## Requirements

- Java 21 or newer
- Maven
- Python 3 (only to serve the static frontend; any static file server works)
- MySQL 8 or newer, running on `localhost:3307`

## Run

Start both servers:

```powershell
.\scripts\run.ps1
```

Then open:

```text
http://localhost:5500
```

Before starting the backend, configure the MySQL password for the current
PowerShell session:

```powershell
$env:CRM_DB_PASSWORD = "your-local-mysql-password"
```

Create the schema once with:

```powershell
Get-Content .\database\mysql_schema.sql | & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -h 127.0.0.1 -P 3307 -u root -p
```

Or run each side separately, in two terminals:

```powershell
.\scripts\run-backend.ps1    # JSON API on http://localhost:8080/api
.\scripts\run-frontend.ps1   # static site on http://localhost:5500
```

The backend reads MySQL settings from environment variables. Defaults are
`CRM_DB_URL=jdbc:mysql://localhost:3307/skyline_crm`, `CRM_DB_USER=root`, and
an empty `CRM_DB_PASSWORD`. Set `CRM_DB_PASSWORD` before starting the app.

To create the database schema, run `database/mysql_schema.sql` in MySQL. The
old `crm.db` file is preserved as a backup. To copy its existing records into
MySQL after creating the schema, set `CRM_DB_PASSWORD` and run:

```powershell
.\scripts\migrate-sqlite-to-mysql.ps1
```

## Demo Login Accounts

```text
ADMIN_OWNER: admin@skyline.local / admin123
EMPLOYEE: employee@skyline.local / employee123
AGENT: agent@skyline.local / agent123
```

The three bootstrap accounts are created in MySQL on the first backend start if they
do not already exist. New Employee and Agent accounts can only be created by the
Admin Owner from `register.html`. Passwords are stored as salted SHA-256 hashes.

## Role Permissions

| Functionality | ADMIN_OWNER | EMPLOYEE | AGENT |
|---|---:|---:|---:|
| Enquiries, follow-ups, leads, bookings, sales | Yes | Yes | Yes |
| Vendor payments | Yes | Yes | No |
| Petty cash | Yes | Yes | No |
| Create users | Yes | No | No |
| Employee and agent counts | Yes | No | No |
| Finance dashboard | Yes | No | No |

The backend returns `401` for unauthenticated requests and `403` when a signed-in
role attempts to access a module outside its permissions. The frontend sidebar is
also generated from the same role policy.

## Architecture

The app is split into a static frontend and a JSON API. There is no npm, no
bundler, and no server-side HTML rendering.

```text
frontend/                    plain HTML + CSS + JavaScript, served as static files
  index.html                 login
  register.html              role-based user registration
  <module>.html              one page per module, form + table markup
  css/style.css              unchanged original stylesheet
  js/api.js                  fetch() client, session token handling
  js/ui.js                   formatting, charts, form binding
  js/pages.js                table rows and dashboards per page
  js/app.js                  builds the shared topbar, sidebar and page frame

backend/                     Java 21 + Maven, com.sun.net.httpserver
  pom.xml
  src/main/java/com/saivandan/crm/App.java        HTTP server, CORS, routing
  src/main/java/com/saivandan/crm/Api.java        endpoint table, auth, sessions
  src/main/java/com/saivandan/crm/Json.java       small JSON writer
  src/main/java/com/saivandan/crm/Reports.java    report and aggregation logic
  src/main/java/com/saivandan/crm/DataStore.java  JDBC MySQL persistence
  src/main/java/com/saivandan/crm/Utils.java      form parsing and helpers
  src/main/java/com/saivandan/crm/model/          Java records

scripts/run.ps1              start API and frontend together
scripts/run-backend.ps1      start the API only
scripts/run-frontend.ps1     start the static server only
scripts/migrate-sqlite-to-mysql.ps1  copy preserved crm.db data to MySQL
database/mysql_schema.sql    MySQL schema matching the existing modules
database/role_migration.sql  map legacy roles to the three-role model
database/business_crud_migration.sql  add safe IDs for booking/sales edit/delete
docs/project-plan.md         full build plan and module order
docs/code-explanations/      walkthrough of each source file
```

### API

- Base URL: `http://localhost:8080/api`
- CORS is enabled for `http://localhost:5500` and `http://127.0.0.1:5500`
- Sign in: `POST /api/login` with form fields `role`, `email`, `password`
- Every other request sends the returned token in the `X-Session-Token` header
- Create records: `POST /api/<module>` with `application/x-www-form-urlencoded`
  fields, the same field names the server-rendered forms used
- Reports: `GET /api/dashboard`, `GET /api/finance-dashboard`,
  `GET /api/<module>` returning `{ "items": [ ... ] }`

## Project Structure

See the Architecture section above for the full tree. The three entry points
are `backend/pom.xml`, `frontend/index.html`, and `scripts/run.ps1`.

## Next Phases

See `docs/project-plan.md` for the full build plan and module order.
