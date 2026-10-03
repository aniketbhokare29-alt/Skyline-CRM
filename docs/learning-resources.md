# Skyline CRM Learning Resources

This guide connects the technologies used in Skyline CRM with the project files where they are used.

## 1. Java Basics and OOP

Project files:

- `backend/src/main/java/com/saivandan/crm/App.java`
- `backend/src/main/java/com/saivandan/crm/DataStore.java`
- `backend/src/main/java/com/saivandan/crm/Reports.java`
- `backend/src/main/java/com/saivandan/crm/model/`

Learn:

- Variables and data types
- Conditions and loops
- Methods
- Classes and objects
- OOP concepts
- Lists and maps
- Exceptions
- Java records

Hindi video:

[Java Tutorial in Hindi - CodeWithHarry](https://www.youtube.com/watch?v=rV_3Lewxx6o)

## 2. HTML

Project file:

- `frontend/*.html` (for example `frontend/enquiries.html`)

Learn:

- HTML page structure
- Forms
- Labels and inputs
- Tables
- Buttons
- Links
- Sections and articles

Hindi video:

[HTML Tutorial for Beginners in Hindi - CodeWithHarry](https://www.youtube.com/watch?v=BsDoLVMnmZs)

## 3. CSS

Project file:

- `frontend/css/style.css`

Learn:

- Selectors
- Colors
- Box model
- Flexbox
- CSS Grid
- Forms and buttons
- Hover states
- Responsive design
- Media queries

Hindi videos:

[CSS Tutorial for Beginners in Hindi - CodeWithHarry](https://www.youtube.com/watch?v=u5-K_ua9sOw)

[Complete CSS Tutorial with Project](https://www.youtube.com/watch?v=Edsxf_NBFrw)

## 4. SQL and SQLite

Project files:

- `crm.db`
- `backend/src/main/java/com/saivandan/crm/DataStore.java`

Learn:

- Databases and tables
- `CREATE TABLE`
- `INSERT`
- `SELECT`
- `UPDATE`
- `DELETE`
- `WHERE`
- `ORDER BY`
- `COUNT`
- Unique constraints

Hindi video:

[Complete SQLite Tutorial in Hindi](https://www.youtube.com/watch?v=cfAadV5K1vE)

## 5. JDBC

Project file:

- `backend/src/main/java/com/saivandan/crm/DataStore.java`

Learn:

- `Connection`
- `Statement`
- `PreparedStatement`
- `ResultSet`
- `DriverManager`
- Insert and select operations
- Closing database resources

Hindi videos:

[Java JDBC Tutorial in Hindi - Hum aur Code](https://www.youtube.com/watch?v=djKQkngqDKA)

[Introduction to JDBC in Hindi - Technical Minds](https://www.youtube.com/watch?v=_V8wJjgQBh0)

## 6. HTTP and Java Web Server

Project file:

- `backend/src/main/java/com/saivandan/crm/App.java`
- `backend/src/main/java/com/saivandan/crm/Api.java`

Learn:

- GET requests
- POST requests
- Routes and URLs
- Form submission
- HTTP responses
- Redirects
- Cookies
- Sessions
- Status codes

Important project routes:

```text
GET  /
POST /login
GET  /dashboard
POST /enquiries
GET  /finance-dashboard
```

The project uses Java's built-in `HttpServer` instead of Spring Boot.

## 7. Maven

Project files:

- `pom.xml`
- `scripts/run.ps1`

Learn:

- What Maven does
- `pom.xml`
- Dependencies
- Maven plugins
- Compile and run commands

Hindi video:

[What is Maven in Java in Hindi](https://www.youtube.com/watch?v=zAmMPM8dIXc)

Useful commands:

```powershell
mvn clean compile
mvn exec:java
```

## 8. PowerShell

Project file:

- `scripts/run.ps1`

Learn:

- Changing folders
- Running scripts
- Checking files
- Stopping a running server

Hindi video:

[PowerShell Tutorial in Hindi](https://www.youtube.com/watch?v=7zXpuwmB0Rw)

## Recommended Learning Order

1. Java basics and OOP
2. HTML forms and tables
3. CSS layout and responsive design
4. SQL and SQLite
5. JDBC
6. HTTP routes and sessions
7. Maven
8. PowerShell

## Project Connection Summary

```text
Java             -> App.java, Api.java, DataStore.java, Reports.java
Java records     -> backend/src/main/java/com/saivandan/crm/model
HTML             -> frontend/*.html
JavaScript       -> frontend/js (api.js, ui.js, pages.js, app.js)
CSS              -> frontend/css/style.css
SQL and SQLite   -> crm.db, DataStore.java
JDBC             -> DataStore.java
HTTP and JSON    -> App.java, Api.java
Maven            -> backend/pom.xml
PowerShell       -> scripts/run.ps1, run-backend.ps1, run-frontend.ps1
```
