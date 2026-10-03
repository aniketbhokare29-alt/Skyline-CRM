# Views.java Explanation

> Last updated: `Views.java` was deleted when the app was split. All of the
> HTML it produced now lives in `frontend/*.html`; the JavaScript equivalents
> of its page builders are spread across `frontend/js/ui.js` (helpers like
> `escape`, `money`, `metric`, `check`, `pieChart`) and `frontend/js/pages.js`
> (row renderers and the two dashboards). The shared topbar/sidebar frame is
> rebuilt by `frontend/js/app.js`. Calculations such as the finance dashboard
> moved to `backend/src/main/java/com/saivandan/crm/Reports.java`.

File:

`src/main/java/com/saivandan/crm/Views.java`

## Purpose

`Views.java` creates the HTML user interface shown in the browser.

## Main page methods

```text
loginPage()
registerPage()
dashboard()
enquiriesPage()
propertiesPage()
paymentsPage()
employeesPage()
vendorsPage()
financeDashboard()
```

## Common layout

`layout()` creates the shared header, sidebar, and content area.

This prevents every page from repeating the same header and navigation code.

## Role-specific UI

`nav(role)` creates the sidebar for the logged-in role.

`dashboard(role)` creates role-specific KPI cards and workspace information.

`analytics(role)` selects charts for the current role.

## Page pattern

Most page methods follow this pattern:

```text
1. Read records from DataStore.
2. Build table rows.
3. Build the form.
4. Return HTML as a String.
```
