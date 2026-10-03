# Model Folder Explanation

Folder:

`src/main/java/com/saivandan/crm/model/`

## Purpose

Each model record represents one type of business data.

## Model mapping

| Model | Represents |
|---|---|
| `Enquiry` | New customer enquiry |
| `PropertyUnit` | Property or flat unit |
| `FollowUp` | Customer follow-up |
| `LeadQualification` | Lead qualification details |
| `SiteVisit` | Property site visit |
| `Negotiation` | Price negotiation |
| `Booking` | Property booking |
| `DocumentCollection` | Customer document checklist |
| `LoanDetail` | Customer loan information |
| `Agreement` | Customer agreement |
| `CustomerPayment` | Customer payment |
| `Possession` | Handover checklist |
| `SupportTicket` | Customer support issue |
| `Employee` | Employee master record |
| `Attendance` | Attendance record |
| `LeaveRequest` | Leave request |
| `SalaryPayment` | Salary payment |
| `RegisteredUser` | Login user |
| `Vendor` | Vendor master record |
| `PurchaseOrder` | Vendor purchase order |
| `VendorBill` | Vendor bill |
| `VendorPayment` | Vendor payment |
| `PettyCashEntry` | Petty cash expense |
| `PettyCashApproval` | Petty cash approval |

Java records mainly store data. Business operations are handled by `App.java`, `Views.java`, and `DataStore.java`.
