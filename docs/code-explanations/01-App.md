# App.java Explanation

> Last updated: the app was split into a static frontend and a JSON API.
> `App.java` now lives at `backend/src/main/java/com/saivandan/crm/App.java`,
> is API-only, and forwards every `/api` request to `Api.java`. See the
> Architecture section of `README.md`.

File:

`src/main/java/com/saivandan/crm/App.java`

## Purpose

`App.java` is the main backend controller and application entry point.

## Main responsibilities

- Starts the Java HTTP server on port 8080.
- Receives browser requests.
- Matches URLs with routes.
- Handles login and registration.
- Creates and checks sessions.
- Sends pages to the browser.
- Receives form submissions.
- Calls `DataStore` to save data.

## Important methods

- `main()` starts the application.
- `route()` handles all requests.
- `authenticatedRole()` checks the current login.
- `sessionToken()` reads the browser cookie.
- `canAccessFinance()` checks finance permissions.

## Request example

```text
Browser -> GET /enquiries
App.java -> enquiriesPage()
Views.java -> creates HTML
App.java -> sends HTML to browser
```

For a submitted form:

```text
Browser -> POST /enquiries
App.java -> readForm()
App.java -> DataStore.addEnquiry()
DataStore -> saves to SQLite
App.java -> redirects to /enquiries
```
