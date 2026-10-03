# Skyline Real Estate CRM - Build Plan

This plan is based on the attached PDF brief. The document is treated as project source material, not as instructions for the assistant.

## Phase 1 - Foundation and CRM Spine

Implemented in the first slice:

- Plain Java web server using `com.sun.net.httpserver.HttpServer`
- Simple role-based login
- Shared page layout with role-specific sidebar navigation
- Dashboard cards by role
- Client enquiry capture
- Property inventory
- Follow-up management
- In-memory data store with seed records

Acceptance checks:

- App starts locally on port 8080.
- User can choose any role from the login page.
- Dashboard changes based on the selected role.
- Enquiries, properties, and follow-ups can be added through simple forms.

## Phase 2 - Lead Qualification and Site Visits

Implemented in the second slice:

- Lead qualification fields: budget, loan required, preferred location, flat type, purchase timeline, purpose
- Lead statuses: Qualified, Not Interested, Future Prospect, Wrong Number, Duplicate
- Site visit booking: visit date, time, pickup required, executive, feedback, status

Acceptance checks:

- Lead qualification records can be added and listed.
- Site visit records can be added and listed.
- Dashboard shows qualified leads and site visit counts.
- Sidebar links open the new Phase 2 modules.

## Phase 3 - Booking, Documents, Loan, Agreement, Payments

Implemented in the third slice:

- Negotiation records: offered price, discount, special offer, management approval
- Booking records: amount, date, unit, floor, flat type, status
- Document checklist: PAN, Aadhaar, photo, address proof, income proof, bank statement
- Loan tracking: bank, amount, EMI, sanction date, current status
- Agreement tracking: agreement date, stamp duty, registration date, registration number
- Customer payment tracking: installment type, paid amount, pending amount, due date, payment history

Acceptance checks:

- Negotiation records can be added and listed.
- Booking records can be added and listed.
- Customer document checklist records can be added and listed.
- Loan records can be added and listed.
- Agreement records can be added and listed.
- Customer payment records can be added and listed.
- Dashboard shows booking count.

## Phase 4 - Possession and Support

Implemented in the fourth slice:

- Possession checklist: final inspection, utility connection, key handover, possession letter
- Possession statuses: Ready, Delivered
- Customer support tickets: maintenance, complaint, service request, documentation, referral

Acceptance checks:

- Possession checklist records can be added and listed.
- Customer support tickets can be added and listed.
- Dashboard shows possession and support ticket counts.
- Sidebar links open the new Phase 4 modules.

## Phase 5 - Employee and Payroll

Implemented in the fifth slice:

- Employee master
- Attendance
- Leave management
- Salary structure
- Incentives, sales commission, bonus
- Deductions: PF, ESIC, professional tax, advance salary, loan recovery
- Salary payment records and salary slip view

Acceptance checks:

- Employee records can be added and listed.
- Attendance records can be added and listed.
- Leave requests can be added and listed.
- Salary payment records can be added and listed.
- Dashboard shows employee and salary run counts.

## Phase 6 - Vendor Management

Implemented:

- Vendor master
- Vendor categories
- Purchase orders
- Vendor bill entry
- Vendor payment details
- Reports: vendor ledger, outstanding bills, monthly vendor payments, purchase summary

Acceptance checks:

- Vendor master records can be added and listed.
- Purchase orders can be added and listed.
- Vendor bills can be added and listed.
- Vendor payments can be added and listed.
- Accountant/Admin/Manager sidebars expose vendor workflows.

## Phase 7 - Petty Cash and Finance Dashboard

Implemented:

- Petty cash entries
- Expense categories
- Approval flow
- Opening and closing cash balance
- Reports: daily cash book, monthly expense report, expense by category, cash balance, audit report
- Finance dashboard: receivables, salary payments, vendor outstanding, petty cash, daily collection, daily expenses, bank balance, profit and loss

Acceptance checks:

- Petty cash entries can be added and listed.
- Petty cash approval records can be added and listed.
- Accountant/Admin/Manager sidebars expose petty cash workflows.
- Accountant/Admin/Manager sidebars expose the finance dashboard.
- Finance dashboard totals are calculated from customer payments, vendor payments, salary payments, and petty cash entries.
- Manager receives a read-only finance summary while Accountant/Admin can use finance entry links.

## Phase 8 - Database and Production Hardening

Build:

- MySQL schema
- DAO/repository layer
- Real authentication and password hashing
- Validation and error handling
- Export to PDF/Excel
- Backup and restore
- Deployment package
