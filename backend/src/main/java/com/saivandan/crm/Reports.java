package com.saivandan.crm;

import com.saivandan.crm.model.Attendance;
import com.saivandan.crm.model.Booking;
import com.saivandan.crm.model.CustomerPayment;
import com.saivandan.crm.model.Enquiry;
import com.saivandan.crm.model.FollowUp;
import com.saivandan.crm.model.LeaveRequest;
import com.saivandan.crm.model.LeadQualification;
import com.saivandan.crm.model.NavItem;
import com.saivandan.crm.model.NavSection;
import com.saivandan.crm.model.PettyCashEntry;
import com.saivandan.crm.model.PropertyUnit;
import com.saivandan.crm.model.RegisteredUser;
import com.saivandan.crm.model.SalaryPayment;
import com.saivandan.crm.model.SiteVisit;
import com.saivandan.crm.model.VendorBill;
import com.saivandan.crm.model.VendorPayment;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.saivandan.crm.Utils.parseMoney;

/**
 * All report and summary computation for the CRM.
 *
 * Every calculation here is carried over unchanged from the original
 * server-rendered {@code Views} class. The only difference is the return type:
 * these methods return plain data so the frontend can render it, instead of
 * returning an HTML string.
 */
public class Reports {

    public record Metric(String label, int value) {
    }

    public record Chart(String title, List<Metric> items) {
    }

    public record Analytics(String title, List<Chart> charts) {
    }

    public record ResultCard(String label, int value, String statusClass) {
    }

    public record CollectionRow(String customer, String installmentType, String paidAmount, String pendingAmount) {
    }

    public record ExpenseRow(String type, String reference, String date, String amount) {
    }

    public record FinanceDashboard(List<Metric> cards,
                                   ResultCard result,
                                   List<CollectionRow> collections,
                                   List<ExpenseRow> expenses,
                                   String managerNote,
                                   List<NavItem> actions) {
    }

    public record Dashboard(String focus,
                            NavItem primaryAction,
                            List<Metric> metrics,
                            List<List<String>> workspaceRows,
                            Analytics analytics) {
    }

    // -----------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------

    public static List<NavSection> navSections(String role) {
        role = RolePolicy.normalize(role);
        if (RolePolicy.ADMIN_OWNER.equals(role)) {
            return List.of(new NavSection("CRM", List.of(
                    new NavItem("/dashboard", "Dashboard"),
                    new NavItem("/enquiries", "All Enquiries"),
                    new NavItem("/followups", "Follow-ups"),
                    new NavItem("/qualification", "Leads"),
                    new NavItem("/bookings", "Bookings"),
                    new NavItem("/payments", "Sales"),
                    new NavItem("/vendor-payments", "Vendor Payments"),
                    new NavItem("/petty-cash", "Petty Cash"),
                    new NavItem("/create-employee", "Create Employee"),
                    new NavItem("/create-agent", "Create Agent"),
                    new NavItem("/finance-dashboard", "Finance Dashboard")
            )));
        }
        if (RolePolicy.AGENT.equals(role)) {
            return List.of(new NavSection("Agent Workspace", List.of(
                    new NavItem("/dashboard", "Dashboard"),
                    new NavItem("/enquiries", "Enquiry Entry"),
                    new NavItem("/followups", "Follow-ups"),
                    new NavItem("/qualification", "Leads"),
                    new NavItem("/bookings", "Booking"),
                    new NavItem("/payments", "Sales")
            )));
        }
        return List.of(new NavSection("Employee Workspace", List.of(
                new NavItem("/dashboard", "Dashboard"),
                new NavItem("/enquiries", "Enquiry Entry"),
                new NavItem("/followups", "Follow-ups"),
                new NavItem("/qualification", "Leads"),
                new NavItem("/bookings", "Booking"),
                new NavItem("/payments", "Sales"),
                new NavItem("/vendor-payments", "Vendor Payment"),
                new NavItem("/petty-cash", "Petty Cash")
        )));
    }

    // -----------------------------------------------------------------
    // Dashboard
    // -----------------------------------------------------------------

    public static int availableUnitCount() {
        return (int) App.STORE.getProperties().stream()
                .filter(p -> "Available".equals(p.availability()))
                .count();
    }

    public static String dashboardFocus(String role) {
        role = RolePolicy.normalize(role);
        return switch (role) {
            case RolePolicy.AGENT -> "Your enquiries, leads, bookings, and sales workspace.";
            case RolePolicy.EMPLOYEE -> "Manage enquiries, follow-ups, bookings, sales, and finance entries.";
            default -> "Admin Owner can manage CRM operations, users, and finance.";
        };
    }

    public static NavItem dashboardPrimaryAction(String role) {
        role = RolePolicy.normalize(role);
        return switch (role) {
            case RolePolicy.ADMIN_OWNER -> new NavItem("/register", "Create User");
            case RolePolicy.AGENT, RolePolicy.EMPLOYEE -> new NavItem("/enquiries", "Add Enquiry");
            default -> new NavItem("/enquiries", "Add Enquiry");
        };
    }

    public static Dashboard dashboard(String role) {
        return new Dashboard(
                dashboardFocus(role),
                dashboardPrimaryAction(role),
                dashboardMetrics(role, availableUnitCount()),
                workspaceRows(role),
                analytics(role)
        );
    }

    public static List<Metric> dashboardMetrics(String role, int available) {
        role = RolePolicy.normalize(role);
        return switch (role) {
            case RolePolicy.AGENT -> List.of(
                    metric("Total Enquiries", App.STORE.getEnquiries().size()),
                    metric("Qualified Leads", qualifiedLeadCount()),
                    metric("Follow-ups", App.STORE.getFollowUps().size()),
                    metric("Bookings", App.STORE.getBookings().size()),
                    metric("Sales", App.STORE.getCustomerPayments().size()));
            case RolePolicy.EMPLOYEE -> List.of(
                    metric("Total Enquiries", App.STORE.getEnquiries().size()),
                    metric("Qualified Leads", qualifiedLeadCount()),
                    metric("Follow-ups", App.STORE.getFollowUps().size()),
                    metric("Bookings", App.STORE.getBookings().size()),
                    metric("Sales", App.STORE.getCustomerPayments().size()),
                    metric("Vendor Payments", App.STORE.getVendorPayments().size()),
                    metric("Petty Cash", App.STORE.getPettyCashEntries().size()));
            default -> List.of(
                    metric("Total Enquiries", App.STORE.getEnquiries().size()),
                    metric("Qualified Leads", qualifiedLeadCount()),
                    metric("Bookings", App.STORE.getBookings().size()),
                    metric("Sales", App.STORE.getCustomerPayments().size()),
                    metric("Employees", App.STORE.countUsersByRole(RolePolicy.EMPLOYEE)),
                    metric("Agents", App.STORE.countUsersByRole(RolePolicy.AGENT)));
        };
    }

    private static int qualifiedLeadCount() {
        int count = 0;
        for (LeadQualification q : App.STORE.getQualifications()) {
            if ("Qualified".equals(q.status())) {
                count++;
            }
        }
        return count;
    }

    private static Metric metric(String label, int value) {
        return new Metric(label, value);
    }

    public static List<List<String>> workspaceRows(String role) {
        role = RolePolicy.normalize(role);
        List<List<String>> rows = new java.util.ArrayList<>();
        switch (role) {
            case RolePolicy.ADMIN_OWNER -> rows.add(List.of("Administration", "All CRM modules and user management", "Employee and agent counts"));
            case RolePolicy.AGENT -> rows.add(List.of("Agent Sales", "Enquiries, leads, follow-ups, bookings", "Sales entry and customer conversion"));
            default -> {
                rows.add(List.of("Employee Sales", "Enquiries, leads, follow-ups, bookings", "Sales entry and customer conversion"));
                rows.add(List.of("Finance Operations", "Vendor payments and petty cash", "Current operational records"));
            }
        }
        return rows;
    }

    // -----------------------------------------------------------------
    // Analytics
    // -----------------------------------------------------------------

    public static Analytics analytics(String role) {
        role = RolePolicy.normalize(role);
        return switch (role) {
            case RolePolicy.AGENT, RolePolicy.EMPLOYEE -> salesAnalytics();
            default -> adminAnalytics();
        };
    }

    private static Analytics salesAnalytics() {
        Map<String, Integer> enquiryStatus = new LinkedHashMap<>();
        Map<String, Integer> followUpStatus = new LinkedHashMap<>();
        Map<String, Integer> siteVisitStatus = new LinkedHashMap<>();
        Map<String, Integer> leadSource = new LinkedHashMap<>();
        for (String label : List.of("New Enquiries", "Qualified", "Not Qualified", "Converted")) enquiryStatus.put(label, 0);
        for (String label : List.of("Completed", "Pending", "Overdue")) followUpStatus.put(label, 0);
        for (String label : List.of("Scheduled", "Completed", "Cancelled")) siteVisitStatus.put(label, 0);
        for (String label : List.of("Website", "Referral", "Walk-in", "Social Media", "Other")) leadSource.put(label, 0);

        for (Enquiry enquiry : App.STORE.getEnquiries()) {
            increment(enquiryStatus, normalizeEnquiryStatus(enquiry.status()));
            increment(leadSource, normalizeLeadSource(enquiry.source()));
        }
        for (FollowUp followUp : App.STORE.getFollowUps()) {
            increment(followUpStatus, followUpStatus(followUp));
        }
        for (SiteVisit visit : App.STORE.getSiteVisits()) {
            increment(siteVisitStatus, normalizeSiteVisitStatus(visit.status()));
        }

        return analytics("Sales Analytics",
                chart("Enquiry Status", enquiryStatus),
                chart("Follow-up Status", followUpStatus),
                chart("Site Visit Status", siteVisitStatus),
                chart("Lead Source", leadSource));
    }

    private static Analytics managerAnalytics() {
        Map<String, Integer> enquiryStatus = groupedEnquiryStatuses();
        Map<String, Integer> propertyAvailability = new LinkedHashMap<>();
        for (String label : List.of("Available", "Booked", "Sold", "Other")) propertyAvailability.put(label, 0);
        for (PropertyUnit unit : App.STORE.getProperties()) {
            String status = unit.availability();
            increment(propertyAvailability, propertyAvailability.containsKey(status) ? status : "Other");
        }
        Map<String, Integer> bookingStatus = new LinkedHashMap<>();
        for (String label : List.of("Booked", "Confirmed", "Cancelled", "Other")) bookingStatus.put(label, 0);
        for (Booking booking : App.STORE.getBookings()) {
            String status = booking.status();
            increment(bookingStatus, bookingStatus.containsKey(status) ? status : "Other");
        }
        return analytics("Management Analytics",
                chart("Enquiry Status", enquiryStatus),
                chart("Property Availability", propertyAvailability),
                chart("Booking Status", bookingStatus));
    }

    private static Analytics accountantAnalytics() {
        Map<String, Integer> paymentStatus = new LinkedHashMap<>();
        for (String label : List.of("Paid", "Pending")) paymentStatus.put(label, 0);
        for (CustomerPayment payment : App.STORE.getCustomerPayments()) {
            increment(paymentStatus, parseMoney(payment.pendingAmount()) > 0 ? "Pending" : "Paid");
        }
        Map<String, Integer> vendorStatus = new LinkedHashMap<>();
        for (String label : List.of("Pending", "Partially Paid", "Fully Paid", "Other")) vendorStatus.put(label, 0);
        for (VendorBill bill : App.STORE.getVendorBills()) {
            String status = bill.status();
            increment(vendorStatus, vendorStatus.containsKey(status) ? status : "Other");
        }
        Map<String, Integer> pettyCash = new LinkedHashMap<>();
        for (PettyCashEntry entry : App.STORE.getPettyCashEntries()) {
            pettyCash.merge(entry.expenseCategory(), 1, Integer::sum);
        }
        return analytics("Accounts Analytics",
                chart("Customer Payment Status", paymentStatus),
                chart("Vendor Bill Status", vendorStatus),
                chart("Petty Cash Categories", pettyCash));
    }

    private static Analytics hrAnalytics() {
        Map<String, Integer> attendance = new LinkedHashMap<>();
        Map<String, Integer> leave = new LinkedHashMap<>();
        Map<String, Integer> salary = new LinkedHashMap<>();
        for (String label : List.of("Present", "Absent", "Half Day", "Other")) attendance.put(label, 0);
        for (String label : List.of("Pending", "Approved", "Rejected", "Other")) leave.put(label, 0);
        for (String label : List.of("Paid", "Pending")) salary.put(label, 0);
        for (Attendance record : App.STORE.getAttendance()) increment(attendance, attendance.containsKey(record.status()) ? record.status() : "Other");
        for (LeaveRequest request : App.STORE.getLeaveRequests()) increment(leave, leave.containsKey(request.status()) ? request.status() : "Other");
        for (SalaryPayment payment : App.STORE.getSalaryPayments()) increment(salary, "Paid".equalsIgnoreCase(payment.paymentStatus()) ? "Paid" : "Pending");
        return analytics("HR Analytics",
                chart("Attendance Status", attendance),
                chart("Leave Status", leave),
                chart("Salary Status", salary));
    }

    private static Analytics adminAnalytics() {
        return analytics("Admin Analytics",
                chart("Enquiry Status", groupedEnquiryStatuses()),
                chart("Lead Source", groupedLeadSources()),
                chart("User Roles", userRoles()));
    }

    private static Map<String, Integer> groupedEnquiryStatuses() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String label : List.of("New Enquiries", "Qualified", "Not Qualified", "Converted")) result.put(label, 0);
        for (Enquiry enquiry : App.STORE.getEnquiries()) increment(result, normalizeEnquiryStatus(enquiry.status()));
        return result;
    }

    private static Map<String, Integer> groupedLeadSources() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String label : List.of("Website", "Referral", "Walk-in", "Social Media", "Other")) result.put(label, 0);
        for (Enquiry enquiry : App.STORE.getEnquiries()) increment(result, normalizeLeadSource(enquiry.source()));
        return result;
    }

    private static Map<String, Integer> userRoles() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String label : List.of(RolePolicy.ADMIN_OWNER, RolePolicy.EMPLOYEE, RolePolicy.AGENT)) result.put(label, 0);
        for (RegisteredUser user : App.STORE.getRegisteredUsers()) increment(result, RolePolicy.normalize(user.role()));
        return result;
    }

    private static Analytics analytics(String title, Chart... charts) {
        return new Analytics(title, List.of(charts));
    }

    private static Chart chart(String title, Map<String, Integer> data) {
        List<Metric> items = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            items.add(new Metric(entry.getKey(), entry.getValue()));
        }
        return new Chart(title, items);
    }

    private static void increment(Map<String, Integer> map, String key) {
        if (map.containsKey(key)) map.merge(key, 1, Integer::sum);
        else map.merge("Other", 1, Integer::sum);
    }

    private static String normalizeEnquiryStatus(String status) {
        if (status == null) return "New Enquiries";
        String value = status.toLowerCase(Locale.ROOT);
        if (value.contains("qualif") && !value.contains("not")) return "Qualified";
        if (value.contains("not") || value.contains("reject")) return "Not Qualified";
        if (value.contains("convert") || value.contains("book")) return "Converted";
        return "New Enquiries";
    }

    private static String normalizeLeadSource(String source) {
        if (source == null) return "Other";
        String value = source.toLowerCase(Locale.ROOT);
        if (value.contains("website") || value.contains("web")) return "Website";
        if (value.contains("referr")) return "Referral";
        if (value.contains("walk")) return "Walk-in";
        if (value.contains("social") || value.contains("facebook") || value.contains("instagram") || value.contains("google") || value.contains("whatsapp")) return "Social Media";
        return "Other";
    }

    private static String normalizeSiteVisitStatus(String status) {
        if (status == null) return "Scheduled";
        String value = status.toLowerCase(Locale.ROOT);
        if (value.contains("cancel")) return "Cancelled";
        if (value.contains("complete") || value.contains("done")) return "Completed";
        return "Scheduled";
    }

    private static String followUpStatus(FollowUp followUp) {
        String remarks = followUp.remarks() == null ? "" : followUp.remarks().toLowerCase(Locale.ROOT);
        if (remarks.contains("complete") || remarks.contains("done")) return "Completed";
        try {
            if (!followUp.nextDate().isBlank() && LocalDate.parse(followUp.nextDate()).isBefore(LocalDate.now())) return "Overdue";
        } catch (Exception ignored) {
            // Existing records may contain display-formatted dates.
        }
        return "Pending";
    }

    // -----------------------------------------------------------------
    // Finance dashboard
    // -----------------------------------------------------------------

    public static FinanceDashboard financeDashboard(String role) {
        role = RolePolicy.normalize(role);
        int collections = 0;
        int receivables = 0;
        for (CustomerPayment payment : App.STORE.getCustomerPayments()) {
            collections += parseMoney(payment.paidAmount());
            receivables += parseMoney(payment.pendingAmount());
        }

        int vendorBills = 0;
        int vendorPaid = 0;
        for (VendorBill bill : App.STORE.getVendorBills()) {
            vendorBills += parseMoney(bill.billAmount());
        }
        for (VendorPayment payment : App.STORE.getVendorPayments()) {
            vendorPaid += parseMoney(payment.paidAmount());
        }

        int salaries = 0;
        for (SalaryPayment salary : App.STORE.getSalaryPayments()) {
            if ("Paid".equalsIgnoreCase(salary.paymentStatus())) {
                salaries += parseMoney(salary.netSalary());
            }
        }

        int pettyCash = 0;
        for (PettyCashEntry entry : App.STORE.getPettyCashEntries()) {
            pettyCash += parseMoney(entry.amount());
        }

        int vendorOutstanding = Math.max(vendorBills - vendorPaid, 0);
        int totalExpenses = vendorPaid + salaries + pettyCash;
        int profitLoss = collections - totalExpenses;
        String resultClass = profitLoss >= 0 ? "status-ok" : "status-warn";
        String resultLabel = profitLoss >= 0 ? "Surplus" : "Deficit";

        List<CollectionRow> collectionRows = new java.util.ArrayList<>();
        for (CustomerPayment payment : App.STORE.getCustomerPayments()) {
            collectionRows.add(new CollectionRow(
                    payment.customer(), payment.installmentType(), payment.paidAmount(), payment.pendingAmount()));
        }

        List<ExpenseRow> expenseRows = new java.util.ArrayList<>();
        for (VendorPayment payment : App.STORE.getVendorPayments()) {
            expenseRows.add(new ExpenseRow("Vendor Payment", payment.vendorName(), payment.paymentDate(), payment.paidAmount()));
        }
        for (SalaryPayment salary : App.STORE.getSalaryPayments()) {
            if ("Paid".equalsIgnoreCase(salary.paymentStatus())) {
                expenseRows.add(new ExpenseRow("Salary", salary.employeeName(), salary.paymentDate(), salary.netSalary()));
            }
        }
        for (PettyCashEntry entry : App.STORE.getPettyCashEntries()) {
            expenseRows.add(new ExpenseRow("Petty Cash", entry.expenseCategory(), entry.date(), entry.amount()));
        }

        String managerNote = "Admin Owner finance summary with collections, expenses, vendor dues, and payroll.";
        List<NavItem> actions = List.of(new NavItem("/payments", "Add Customer Payment"));

        List<Metric> cards = List.of(
                new Metric("Receivables", receivables),
                new Metric("Collections", collections),
                new Metric("Vendor Outstanding", vendorOutstanding),
                new Metric("Vendor Paid", vendorPaid),
                new Metric("Salary Paid", salaries),
                new Metric("Petty Cash", pettyCash),
                new Metric("Total Expenses", totalExpenses),
                new Metric("Bank Balance (Net)", collections - totalExpenses)
        );

        return new FinanceDashboard(
                cards,
                new ResultCard(resultLabel, profitLoss, resultClass),
                collectionRows,
                expenseRows,
                managerNote,
                actions
        );
    }

    // -----------------------------------------------------------------
    // Per-page summaries
    // -----------------------------------------------------------------

    public static int paymentsTotalPaid() {
        int total = 0;
        for (CustomerPayment p : App.STORE.getCustomerPayments()) {
            total += parseMoney(p.paidAmount());
        }
        return total;
    }

    public static int paymentsTotalPending() {
        int total = 0;
        for (CustomerPayment p : App.STORE.getCustomerPayments()) {
            total += parseMoney(p.pendingAmount());
        }
        return total;
    }

    public static int salaryTotalGross() {
        int total = 0;
        for (SalaryPayment s : App.STORE.getSalaryPayments()) {
            total += parseMoney(s.grossSalary());
        }
        return total;
    }

    public static int salaryTotalNet() {
        int total = 0;
        for (SalaryPayment s : App.STORE.getSalaryPayments()) {
            total += parseMoney(s.netSalary());
        }
        return total;
    }

    public static int vendorBillsOutstanding() {
        int outstanding = 0;
        for (VendorBill b : App.STORE.getVendorBills()) {
            outstanding += "Fully Paid".equals(b.status()) ? 0 : parseMoney(b.billAmount());
        }
        return outstanding;
    }

    public static int vendorPaymentsTotalPaid() {
        int total = 0;
        for (VendorPayment p : App.STORE.getVendorPayments()) {
            total += parseMoney(p.paidAmount());
        }
        return total;
    }

    public static int vendorPaymentsTotalBalance() {
        int total = 0;
        for (VendorPayment p : App.STORE.getVendorPayments()) {
            total += parseMoney(p.balanceAmount());
        }
        return total;
    }

    public static int pettyCashSpent() {
        int spent = 0;
        for (PettyCashEntry e : App.STORE.getPettyCashEntries()) {
            spent += parseMoney(e.amount());
        }
        return spent;
    }
}
