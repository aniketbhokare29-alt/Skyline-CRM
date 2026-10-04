package com.saivandan.crm;

import com.saivandan.crm.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DataStore {
    private static final String DEFAULT_DB_URL =
            "jdbc:mysql://localhost:3307/skyline_crm?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_URL = setting("CRM_DB_URL", DEFAULT_DB_URL);
    private static final String DB_USER = setting("CRM_DB_USER", "root");
    private static final String DB_PASSWORD = setting("CRM_DB_PASSWORD", "");

    private static final boolean IS_SQLITE = DB_URL.startsWith("jdbc:sqlite:");

    /** Reports the configured target without exposing the password. */
    public static String dbPath() {
        return DB_URL;
    }

    private static String setting(String name, String fallback) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv(name);
        return environment == null || environment.isBlank() ? fallback : environment;
    }

    static Connection openConnection() throws SQLException {
        if (IS_SQLITE) {
            return DriverManager.getConnection(DB_URL);
        }
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private String autoIncPk() {
        return IS_SQLITE ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
    }

    private String addColumnId() {
        return IS_SQLITE ? "ADD COLUMN id INTEGER PRIMARY KEY AUTOINCREMENT" : "ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST";
    }

    private String modifyColumn(String table, String column, String type) {
        if (IS_SQLITE) {
            return ""; // SQLite doesn't support ALTER TABLE MODIFY COLUMN easily
        }
        return "ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + type;
    }

    private String upsertSessions() {
        if (IS_SQLITE) {
            return "INSERT INTO sessions (token, role, createdAt) VALUES (?, ?, ?) ON CONFLICT(token) DO UPDATE SET role = excluded.role, createdAt = excluded.createdAt";
        }
        return "INSERT INTO sessions (token, role, createdAt) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE role = VALUES(role), createdAt = VALUES(createdAt)";
    }

    private String upsertRegisteredUser() {
        if (IS_SQLITE) {
            return "INSERT INTO registeredusers (name, mobile, email, role, password, createdDate) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(email) DO UPDATE SET role = excluded.role, name = excluded.name, password = excluded.password, createdDate = excluded.createdDate";
        }
        return "INSERT INTO registeredusers (name, mobile, email, role, password, createdDate) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE role = VALUES(role), name = VALUES(name), password = VALUES(password), createdDate = VALUES(createdDate)";
    }

    /** Maps SQLite errors to HTTP-friendly exceptions (409 for duplicates). */
    static RuntimeException mapSaveError(SQLException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        String lower = msg.toLowerCase();
        if (e.getErrorCode() == 19 || e.getErrorCode() == 1062 || lower.contains("duplicate")
                || lower.contains("unique") || lower.contains("primary") || lower.contains("constraint")) {
            return new DuplicateException("Duplicate entry: " + msg, e);
        }
        return new RuntimeException("Database error: " + msg, e);
    }

    /** Sessions survive restarts; tokens expire after 24h. */
    public void saveSession(String token, String role) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSessions())) {
            ps.setString(1, token);
            ps.setString(2, RolePolicy.normalize(role));
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public Optional<String> findSessionRole(String token) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT role, createdAt FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long age = System.currentTimeMillis() - rs.getLong("createdAt");
                    if (age > 24L * 60 * 60 * 1000) {
                        deleteSession(token);
                        return Optional.empty();
                    }
                    return Optional.of(RolePolicy.normalize(rs.getString("role")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return Optional.empty();
    }

    public void deleteSession(String token) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public DataStore() {
        System.out.println("Skyline CRM database: " + DB_URL);
        initDb();
    }

    private void initDb() {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {
            String pk = autoIncPk();
            stmt.execute("CREATE TABLE IF NOT EXISTS enquiries (id " + pk + ", name VARCHAR(255), mobile VARCHAR(255), email VARCHAR(255), city VARCHAR(255), budget VARCHAR(255), interestedProperty VARCHAR(255), source VARCHAR(255), status VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS properties (unit VARCHAR(191), wing VARCHAR(255), floor VARCHAR(255), flatType VARCHAR(255), area VARCHAR(255), price VARCHAR(255), parking VARCHAR(255), availability VARCHAR(255), amenities VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS followups (id " + pk + ", customer VARCHAR(255), type VARCHAR(255), date VARCHAR(255), time VARCHAR(255), nextDate VARCHAR(255), executive VARCHAR(255), remarks VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS qualifications (id " + pk + ", customer VARCHAR(255), budget VARCHAR(255), loanRequired VARCHAR(255), preferredLocation VARCHAR(255), flatType VARCHAR(255), purchaseTimeline VARCHAR(255), purpose VARCHAR(255), status VARCHAR(255), notes VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS sitevisits (id " + pk + ", customer VARCHAR(255), visitDate VARCHAR(255), visitTime VARCHAR(255), pickupRequired VARCHAR(255), executive VARCHAR(255), status VARCHAR(255), feedback VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS negotiations (customer VARCHAR(255), unit VARCHAR(255), offeredPrice VARCHAR(255), discount VARCHAR(255), managementApproval VARCHAR(255), specialOffer VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS bookings (id " + pk + ", customer VARCHAR(255), bookingAmount VARCHAR(255), bookingDate VARCHAR(255), unit VARCHAR(255), floor VARCHAR(255), flatType VARCHAR(255), status VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS documentcollections (customer VARCHAR(255), panCard VARCHAR(255), aadhaarCard VARCHAR(255), passportPhoto VARCHAR(255), addressProof VARCHAR(255), incomeProof VARCHAR(255), bankStatement VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS loans (customer VARCHAR(255), bankName VARCHAR(255), loanAmount VARCHAR(255), emi VARCHAR(255), sanctionDate VARCHAR(255), status VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS agreements (customer VARCHAR(255), unit VARCHAR(255), agreementDate VARCHAR(255), stampDuty VARCHAR(255), registrationDate VARCHAR(255), registrationNumber VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS customerpayments (id " + pk + ", customer VARCHAR(255), installmentType VARCHAR(255), totalCost VARCHAR(255), paidAmount VARCHAR(255), pendingAmount VARCHAR(255), dueDate VARCHAR(255), history VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS possessions (customer VARCHAR(255), unit VARCHAR(255), finalInspection VARCHAR(255), utilityConnection VARCHAR(255), keyHandover VARCHAR(255), possessionLetter VARCHAR(255), status VARCHAR(255), remarks VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS supporttickets (customer VARCHAR(255), unit VARCHAR(255), category VARCHAR(255), priority VARCHAR(255), status VARCHAR(255), assignedTo VARCHAR(255), description VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS employees (employeeId VARCHAR(191), name VARCHAR(255), department VARCHAR(255), designation VARCHAR(255), mobile VARCHAR(255), email VARCHAR(255), dateOfJoining VARCHAR(255), salaryStructure VARCHAR(255), bankDetails VARCHAR(255), identityDetails VARCHAR(255), statutoryDetails VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS attendance (employeeId VARCHAR(255), employeeName VARCHAR(255), date VARCHAR(255), status VARCHAR(255), overtimeHours VARCHAR(255), remarks VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS leaverequests (employeeId VARCHAR(255), employeeName VARCHAR(255), fromDate VARCHAR(255), toDate VARCHAR(255), leaveType VARCHAR(255), status VARCHAR(255), reason VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS salarypayments (employeeId VARCHAR(255), employeeName VARCHAR(255), salaryMonth VARCHAR(255), basicSalary VARCHAR(255), hra VARCHAR(255), incentives VARCHAR(255), bonus VARCHAR(255), deductions VARCHAR(255), grossSalary VARCHAR(255), netSalary VARCHAR(255), paymentDate VARCHAR(255), paymentMode VARCHAR(255), paymentStatus VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS registeredusers (name VARCHAR(255), mobile VARCHAR(255), email VARCHAR(191) UNIQUE, role VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE', password VARCHAR(255), createdDate VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS vendors (vendorName VARCHAR(191), companyName VARCHAR(255), gstNumber VARCHAR(255), panNumber VARCHAR(255), contactPerson VARCHAR(255), mobile VARCHAR(255), email VARCHAR(255), address VARCHAR(255), bankDetails VARCHAR(255), category VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS purchaseorders (poNumber VARCHAR(191), date VARCHAR(255), vendorName VARCHAR(255), material VARCHAR(255), quantity VARCHAR(255), rate VARCHAR(255), gst VARCHAR(255), totalAmount VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS vendorbills (vendorName VARCHAR(95), invoiceNumber VARCHAR(95), invoiceDate VARCHAR(255), billAmount VARCHAR(255), gst VARCHAR(255), dueDate VARCHAR(255), status VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS vendorpayments (vendorName VARCHAR(255), paymentDate VARCHAR(255), paymentMode VARCHAR(255), transactionNumber VARCHAR(255), bank VARCHAR(255), paidAmount VARCHAR(255), balanceAmount VARCHAR(255), status VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS pettycashentries (date VARCHAR(255), voucherNumber VARCHAR(191), expenseCategory VARCHAR(255), description VARCHAR(255), employeeName VARCHAR(255), amount VARCHAR(255), paymentMode VARCHAR(255), approvedBy VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS pettycashapprovals (requestedBy VARCHAR(255), approvedBy VARCHAR(255), amount VARCHAR(255), remarks VARCHAR(255), status VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS sessions (token VARCHAR(191) PRIMARY KEY, role VARCHAR(255) NOT NULL, createdAt BIGINT NOT NULL)");
            // Repair columns from partial/older MySQL schema attempts before indexes are added.
            List<String> migrations = new ArrayList<>();
            if (!IS_SQLITE) {
                migrations.addAll(List.of(
                        "ALTER TABLE properties MODIFY COLUMN unit VARCHAR(191)",
                        "ALTER TABLE employees MODIFY COLUMN employeeId VARCHAR(191)",
                        "ALTER TABLE registeredusers MODIFY COLUMN email VARCHAR(191)",
                        "ALTER TABLE purchaseorders MODIFY COLUMN poNumber VARCHAR(191)",
                        "ALTER TABLE vendors MODIFY COLUMN vendorName VARCHAR(191)",
                        "ALTER TABLE vendorbills MODIFY COLUMN vendorName VARCHAR(95), MODIFY COLUMN invoiceNumber VARCHAR(95)",
                        "ALTER TABLE pettycashentries MODIFY COLUMN voucherNumber VARCHAR(191)",
                        "ALTER TABLE registeredusers MODIFY COLUMN role VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE'",
                        "ALTER TABLE sessions MODIFY COLUMN token VARCHAR(191)"
                ));
            }
            migrations.addAll(List.of(
                    "ALTER TABLE bookings " + addColumnId(),
                    "ALTER TABLE customerpayments " + addColumnId(),
                    "ALTER TABLE enquiries " + addColumnId(),
                    "ALTER TABLE followups " + addColumnId(),
                    "ALTER TABLE qualifications " + addColumnId(),
                    "ALTER TABLE sitevisits " + addColumnId(),
                    "ALTER TABLE negotiations " + addColumnId(),
                    "ALTER TABLE loans " + addColumnId(),
                    "ALTER TABLE agreements " + addColumnId(),
                    "ALTER TABLE documentcollections " + addColumnId(),
                    "ALTER TABLE possessions " + addColumnId(),
                    "ALTER TABLE supporttickets " + addColumnId(),
                    "ALTER TABLE employees " + addColumnId(),
                    "ALTER TABLE attendance " + addColumnId(),
                    "ALTER TABLE leaverequests " + addColumnId(),
                    "ALTER TABLE salarypayments " + addColumnId(),
                    "ALTER TABLE vendors " + addColumnId(),
                    "ALTER TABLE purchaseorders " + addColumnId(),
                    "ALTER TABLE vendorbills " + addColumnId(),
                    "ALTER TABLE vendorpayments " + addColumnId(),
                    "ALTER TABLE pettycashentries " + addColumnId(),
                    "ALTER TABLE pettycashapprovals " + addColumnId()
            ));
            for (String migration : migrations) {
                try {
                    stmt.execute(migration);
                } catch (SQLException migrationError) {
                    // Existing columns are expected to reject ALTER TABLE. Log all
                    // other failures so a broken schema cannot be mistaken for a
                    // healthy startup.
                    if (!isAlreadyApplied(migrationError)) {
                        System.err.println("Database migration skipped: " + migration);
                        migrationError.printStackTrace(System.err);
                    }
                }
            }
            normalizeRegisteredUserRoles(stmt);
            try {
                stmt.execute("ALTER TABLE registeredusers ADD CONSTRAINT chk_registeredusers_role CHECK (role IN ('ADMIN_OWNER', 'EMPLOYEE', 'AGENT'))");
            } catch (SQLException constraintError) {
                // The constraint may already exist, or the server may not enforce CHECK constraints.
                if (!isAlreadyApplied(constraintError)) {
                    System.err.println("Could not validate registered user roles.");
                    constraintError.printStackTrace(System.err);
                }
            }
            ensureBootstrapUsers(conn);
            syncEmployeeRecords(conn);
            // Natural keys: prevent accidental double entry. IF NOT EXISTS keeps old DBs working.
            for (String idx : new String[]{
                    "CREATE UNIQUE INDEX idx_properties_unit ON properties(unit)",
                    "CREATE UNIQUE INDEX idx_employees_id ON employees(employeeId)",
                    "CREATE UNIQUE INDEX idx_po_number ON purchaseorders(poNumber)",
                    "CREATE UNIQUE INDEX idx_vendors_name ON vendors(vendorName)",
                    "CREATE UNIQUE INDEX idx_vendorbills_vendor_inv ON vendorbills(vendorName, invoiceNumber)",
                    "CREATE UNIQUE INDEX idx_pettycash_voucher ON pettycashentries(voucherNumber)"}) {
                try {
                    stmt.execute(idx);
                } catch (SQLException dup) {
                    // Pre-existing duplicate rows (e.g. old attendance data) must not block startup.
                    System.out.println("Skyline CRM index skipped: " + dup.getMessage());
                }
            }
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM enquiries");
            boolean fresh = rs.next() && rs.getInt(1) == 0;
            rs.close();
            if (fresh) {
                seedData(conn);
            }
            // Older databases were created before newer modules existed, so their
            // tables stay empty forever when we only seed fresh databases.
            // Fill any table that is still empty.
            seedMissing(conn);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    private boolean isAlreadyApplied(SQLException error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        return message.contains("duplicate column")
                || message.contains("duplicate key name")
                || message.contains("already exists")
                || message.contains("check that column/key exists")
                || message.contains("duplicate constraint")
                || message.contains("multiple primary key");
    }

    private void normalizeRegisteredUserRoles(Statement stmt) throws SQLException {
        stmt.executeUpdate("UPDATE registeredusers SET role = 'ADMIN_OWNER' WHERE role IN ('Admin', 'Admin Owner', 'admin_owner')");
        stmt.executeUpdate("UPDATE registeredusers SET role = 'AGENT' WHERE lower(role) = 'agent'");
        stmt.executeUpdate("UPDATE registeredusers SET role = 'EMPLOYEE' WHERE role IS NULL OR trim(role) = '' OR role NOT IN ('ADMIN_OWNER', 'EMPLOYEE', 'AGENT')");
    }

    private void ensureBootstrapUsers(Connection conn) throws SQLException {
        String sql = upsertRegisteredUser();
        String today = LocalDate.now().toString();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bootstrapUser(ps, "Admin Owner", "", "admin@skyline.local", RolePolicy.ADMIN_OWNER, "admin123", today);
            bootstrapUser(ps, "Employee User", "", "employee@skyline.local", RolePolicy.EMPLOYEE, "employee123", today);
            bootstrapUser(ps, "Agent User", "", "agent@skyline.local", RolePolicy.AGENT, "agent123", today);
        }
    }

    /** Keeps Employee login accounts visible in the Employees workspace. */
    private void syncEmployeeRecords(Connection conn) throws SQLException {
        String usersSql = "SELECT name, mobile, email FROM registeredusers WHERE role = 'EMPLOYEE'";
        try (PreparedStatement users = conn.prepareStatement(usersSql);
             ResultSet rs = users.executeQuery()) {
            while (rs.next()) {
                ensureEmployeeRecord(conn, rs.getString("name"), rs.getString("mobile"), rs.getString("email"));
            }
        }
    }

    private void ensureEmployeeRecord(Connection conn, String name, String mobile, String email) throws SQLException {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isBlank()) {
            return;
        }
        try (PreparedStatement exists = conn.prepareStatement(
                "SELECT COUNT(*) FROM employees WHERE lower(email) = ?")) {
            exists.setString(1, normalizedEmail);
            try (ResultSet rs = exists.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        String employeeId = "EMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        insertEmployee(conn, new Employee(
                employeeId,
                name == null ? "" : name,
                "General",
                "Employee",
                mobile == null ? "" : mobile,
                normalizedEmail,
                LocalDate.now().toString(),
                "Not configured",
                "",
                "",
                ""
        ));
    }

    private void bootstrapUser(PreparedStatement ps, String name, String mobile, String email,
                               String role, String password, String createdDate) throws SQLException {
        ps.setString(1, name);
        ps.setString(2, mobile);
        ps.setString(3, email);
        ps.setString(4, role);
        ps.setString(5, hashPassword(password));
        ps.setString(6, createdDate);
        ps.addBatch();
        ps.executeBatch();
        ps.clearBatch();
    }

    private void seedData(Connection conn) {
        try {
            insertEnquiry(conn, new Enquiry("Amit Patil", "9876543210", "amit@example.com", "Pune", "55L - 70L", "2 BHK", "Website", "New Lead"));
            insertEnquiry(conn, new Enquiry("Priya Shah", "9123456780", "priya@example.com", "Mumbai", "80L - 95L", "3 BHK", "Referral", "Qualified"));
            insertPropertyUnit(conn, new PropertyUnit("A-101", "A", "1", "2 BHK", "680 / 910 sq ft", "6800000", "Yes", "Available", "Lift, parking, security"));
            insertPropertyUnit(conn, new PropertyUnit("B-403", "B", "4", "3 BHK", "910 / 1220 sq ft", "9400000", "Yes", "Booked", "Garden view, lift, clubhouse"));
            insertFollowUp(conn, new FollowUp("Amit Patil", "Phone Call", LocalDate.now().toString(), "10:30", LocalDate.now().plusDays(2).toString(), "Sneha", "Asked for weekend site visit."));
            insertLeadQualification(conn, new LeadQualification("Amit Patil", "55L - 70L", "Yes", "Baner", "2 BHK", "Within 1 Month", "Self Use", "Qualified", "Ready for site visit."));
            insertLeadQualification(conn, new LeadQualification("Priya Shah", "80L - 95L", "No", "Wakad", "3 BHK", "1 - 3 Months", "Investment", "Future Prospect", "Wants possession timeline details."));
            insertSiteVisit(conn, new SiteVisit("Amit Patil", LocalDate.now().plusDays(1).toString(), "11:00", "No", "Sneha", "Scheduled", "Interested in A wing units."));
            insertNegotiation(conn, new Negotiation("Amit Patil", "A-101", "6600000", "200000", "Approved", "Free covered parking."));
            insertBooking(conn, new Booking("Amit Patil", "100000", LocalDate.now().toString(), "A-101", "1", "2 BHK", "Confirmed"));
            insertDocumentCollection(conn, new DocumentCollection("Amit Patil", "Yes", "Yes", "Yes", "No", "No", "Yes"));
            insertLoanDetail(conn, new LoanDetail("Amit Patil", "HDFC Bank", "4500000", "42000", LocalDate.now().plusDays(12).toString(), "Bank Verification"));
            insertAgreement(conn, new Agreement("Amit Patil", "A-101", LocalDate.now().plusDays(20).toString(), "420000", LocalDate.now().plusDays(25).toString(), "REG-PENDING"));
            insertCustomerPayment(conn, new CustomerPayment("Amit Patil", "Booking Amount", "6800000", "100000", "6700000", LocalDate.now().plusDays(15).toString(), "Booking amount received."));
            insertPossession(conn, new Possession("Amit Patil", "A-101", "Yes", "No", "No", "No", "Ready", "Final inspection complete, utility connection pending."));
            insertSupportTicket(conn, new SupportTicket("Priya Shah", "B-403", "Documentation", "Normal", "Open", "Admin", "Requested updated allotment copy."));
            insertEmployee(conn, new Employee("EMP-001", "Sneha Kulkarni", "Sales", "Sales Executive", "9876500001", "sneha@skyline.local", LocalDate.now().minusYears(1).toString(), "Basic + HRA + Commission", "HDFC 1234", "PAN/Aadhaar on file", "PF active"));
            insertEmployee(conn, new Employee("EMP-002", "Rahul Deshmukh", "Accounts", "Accountant", "9876500002", "rahul@skyline.local", LocalDate.now().minusMonths(8).toString(), "Basic + HRA", "ICICI 5678", "PAN/Aadhaar on file", "PF/ESIC active"));
            insertAttendance(conn, new Attendance("EMP-001", "Sneha Kulkarni", LocalDate.now().toString(), "Present", "1", "Morning follow-up calls and site visit."));
            insertLeaveRequest(conn, new LeaveRequest("EMP-002", "Rahul Deshmukh", LocalDate.now().plusDays(3).toString(), LocalDate.now().plusDays(4).toString(), "Casual Leave", "Pending", "Personal work."));
            insertSalaryPayment(conn, new SalaryPayment("EMP-001", "Sneha Kulkarni", "September 2026", "25000", "10000", "8000", "0", "3000", "43000", "40000", LocalDate.now().toString(), "Bank Transfer", "Pending"));
            insertVendor(conn, new Vendor("Mohan Electricals", "Mohan Electricals Pvt Ltd", "27AAECM1234K1Z5", "AAECM1234K", "Mohan Patil", "9876500101", "billing@mohanelectricals.local", "Pune", "HDFC 9988", "Electrical Contractor"));
            insertPurchaseOrder(conn, new PurchaseOrder("PO-001", LocalDate.now().toString(), "Mohan Electricals", "Wiring material", "120", "450", "18%", "63720"));
            insertVendorBill(conn, new VendorBill("Mohan Electricals", "INV-1001", LocalDate.now().toString(), "63720", "9720", LocalDate.now().plusDays(15).toString(), "Pending"));
            insertVendorPayment(conn, new VendorPayment("Mohan Electricals", LocalDate.now().toString(), "Bank Transfer", "UTR100200", "HDFC", "25000", "38720", "Partially Paid"));
            insertPettyCashEntry(conn, new PettyCashEntry(LocalDate.now().toString(), "PC-001", "Tea & Snacks", "Site meeting refreshments", "Sneha Kulkarni", "850", "Cash", "Admin"));
            insertPettyCashApproval(conn, new PettyCashApproval("Sneha Kulkarni", "Admin", "850", "Approved for site meeting.", "Approved"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Fills tables that are empty because the database was created before that
     * module existed. Never deletes user data; only inserts demo rows into
     * tables with zero rows.
     */
    private void seedMissing(Connection conn) {
        try {
            if (tableCount(conn, "enquiries") == 0) {
                insertEnquiry(conn, new Enquiry("Amit Patil", "9876543210", "amit@example.com", "Pune", "55L - 70L", "2 BHK", "Website", "New Lead"));
                insertEnquiry(conn, new Enquiry("Priya Shah", "9123456780", "priya@example.com", "Mumbai", "80L - 95L", "3 BHK", "Referral", "Qualified"));
            }
            if (tableCount(conn, "properties") == 0) {
                insertPropertyUnit(conn, new PropertyUnit("A-101", "A", "1", "2 BHK", "680 / 910 sq ft", "6800000", "Yes", "Available", "Lift, parking, security"));
                insertPropertyUnit(conn, new PropertyUnit("B-403", "B", "4", "3 BHK", "910 / 1220 sq ft", "9400000", "Yes", "Booked", "Garden view, lift, clubhouse"));
            }
            if (tableCount(conn, "followups") == 0) {
                insertFollowUp(conn, new FollowUp("Amit Patil", "Phone Call", LocalDate.now().toString(), "10:30", LocalDate.now().plusDays(2).toString(), "Sneha", "Asked for weekend site visit."));
            }
            if (tableCount(conn, "qualifications") == 0) {
                insertLeadQualification(conn, new LeadQualification("Amit Patil", "55L - 70L", "Yes", "Baner", "2 BHK", "Within 1 Month", "Self Use", "Qualified", "Ready for site visit."));
                insertLeadQualification(conn, new LeadQualification("Priya Shah", "80L - 95L", "No", "Wakad", "3 BHK", "1 - 3 Months", "Investment", "Future Prospect", "Wants possession timeline details."));
            }
            if (tableCount(conn, "sitevisits") == 0) {
                insertSiteVisit(conn, new SiteVisit("Amit Patil", LocalDate.now().plusDays(1).toString(), "11:00", "No", "Sneha", "Scheduled", "Interested in A wing units."));
            }
            if (tableCount(conn, "negotiations") == 0) {
                insertNegotiation(conn, new Negotiation("Amit Patil", "A-101", "6600000", "200000", "Approved", "Free covered parking."));
            }
            if (tableCount(conn, "bookings") == 0) {
                insertBooking(conn, new Booking("Amit Patil", "100000", LocalDate.now().toString(), "A-101", "1", "2 BHK", "Confirmed"));
            }
            if (tableCount(conn, "documentcollections") == 0) {
                insertDocumentCollection(conn, new DocumentCollection("Amit Patil", "Yes", "Yes", "Yes", "No", "No", "Yes"));
            }
            if (tableCount(conn, "loans") == 0) {
                insertLoanDetail(conn, new LoanDetail("Amit Patil", "HDFC Bank", "4500000", "42000", LocalDate.now().plusDays(12).toString(), "Bank Verification"));
            }
            if (tableCount(conn, "agreements") == 0) {
                insertAgreement(conn, new Agreement("Amit Patil", "A-101", LocalDate.now().plusDays(20).toString(), "420000", LocalDate.now().plusDays(25).toString(), "REG-PENDING"));
            }
            if (tableCount(conn, "customerpayments") == 0) {
                insertCustomerPayment(conn, new CustomerPayment("Amit Patil", "Booking Amount", "6800000", "100000", "6700000", LocalDate.now().plusDays(15).toString(), "Booking amount received."));
            }
            if (tableCount(conn, "possessions") == 0) {
                insertPossession(conn, new Possession("Amit Patil", "A-101", "Yes", "No", "No", "No", "Ready", "Final inspection complete, utility connection pending."));
            }
            if (tableCount(conn, "supporttickets") == 0) {
                insertSupportTicket(conn, new SupportTicket("Priya Shah", "B-403", "Documentation", "Normal", "Open", "Admin", "Requested updated allotment copy."));
            }
            if (tableCount(conn, "employees") == 0) {
                insertEmployee(conn, new Employee("EMP-001", "Sneha Kulkarni", "Sales", "Sales Executive", "9876500001", "sneha@skyline.local", LocalDate.now().minusYears(1).toString(), "Basic + HRA + Commission", "HDFC 1234", "PAN/Aadhaar on file", "PF active"));
                insertEmployee(conn, new Employee("EMP-002", "Rahul Deshmukh", "Accounts", "Accountant", "9876500002", "rahul@skyline.local", LocalDate.now().minusMonths(8).toString(), "Basic + HRA", "ICICI 5678", "PAN/Aadhaar on file", "PF/ESIC active"));
            }
            if (tableCount(conn, "attendance") == 0) {
                insertAttendance(conn, new Attendance("EMP-001", "Sneha Kulkarni", LocalDate.now().toString(), "Present", "1", "Morning follow-up calls and site visit."));
            }
            if (tableCount(conn, "leaverequests") == 0) {
                insertLeaveRequest(conn, new LeaveRequest("EMP-002", "Rahul Deshmukh", LocalDate.now().plusDays(3).toString(), LocalDate.now().plusDays(4).toString(), "Casual Leave", "Pending", "Personal work."));
            }
            if (tableCount(conn, "salarypayments") == 0) {
                insertSalaryPayment(conn, new SalaryPayment("EMP-001", "Sneha Kulkarni", "September 2026", "25000", "10000", "8000", "0", "3000", "43000", "40000", LocalDate.now().toString(), "Bank Transfer", "Pending"));
            }
            if (tableCount(conn, "vendors") == 0) {
                insertVendor(conn, new Vendor("Mohan Electricals", "Mohan Electricals Pvt Ltd", "27AAECM1234K1Z5", "AAECM1234K", "Mohan Patil", "9876500101", "billing@mohanelectricals.local", "Pune", "HDFC 9988", "Electrical Contractor"));
            }
            if (tableCount(conn, "purchaseorders") == 0) {
                insertPurchaseOrder(conn, new PurchaseOrder("PO-001", LocalDate.now().toString(), "Mohan Electricals", "Wiring material", "120", "450", "18%", "63720"));
            }
            if (tableCount(conn, "vendorbills") == 0) {
                insertVendorBill(conn, new VendorBill("Mohan Electricals", "INV-1001", LocalDate.now().toString(), "63720", "9720", LocalDate.now().plusDays(15).toString(), "Pending"));
            }
            if (tableCount(conn, "vendorpayments") == 0) {
                insertVendorPayment(conn, new VendorPayment("Mohan Electricals", LocalDate.now().toString(), "Bank Transfer", "UTR100200", "HDFC", "25000", "38720", "Partially Paid"));
            }
            if (tableCount(conn, "pettycashentries") == 0) {
                insertPettyCashEntry(conn, new PettyCashEntry(LocalDate.now().toString(), "PC-001", "Tea & Snacks", "Site meeting refreshments", "Sneha Kulkarni", "850", "Cash", "Admin"));
            }
            if (tableCount(conn, "pettycashapprovals") == 0) {
                insertPettyCashApproval(conn, new PettyCashApproval("Sneha Kulkarni", "Admin", "850", "Approved for site meeting.", "Approved"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int tableCount(Connection conn, String table) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM `" + table + "`")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void insertEnquiry(Connection conn, Enquiry obj) throws SQLException {
        String sql = "INSERT INTO enquiries (name, mobile, email, city, budget, interestedProperty, source, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.name());
            pstmt.setString(2, obj.mobile());
            pstmt.setString(3, obj.email());
            pstmt.setString(4, obj.city());
            pstmt.setString(5, obj.budget());
            pstmt.setString(6, obj.interestedProperty());
            pstmt.setString(7, obj.source());
            pstmt.setString(8, obj.status());
            pstmt.executeUpdate();
        }
    }

    private void insertPropertyUnit(Connection conn, PropertyUnit obj) throws SQLException {
        String sql = "INSERT INTO properties (unit, wing, floor, flatType, area, price, parking, availability, amenities) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.unit());
            pstmt.setString(2, obj.wing());
            pstmt.setString(3, obj.floor());
            pstmt.setString(4, obj.flatType());
            pstmt.setString(5, obj.area());
            pstmt.setString(6, obj.price());
            pstmt.setString(7, obj.parking());
            pstmt.setString(8, obj.availability());
            pstmt.setString(9, obj.amenities());
            pstmt.executeUpdate();
        }
    }

    private void insertFollowUp(Connection conn, FollowUp obj) throws SQLException {
        String sql = "INSERT INTO followups (customer, type, date, time, nextDate, executive, remarks) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.type());
            pstmt.setString(3, obj.date());
            pstmt.setString(4, obj.time());
            pstmt.setString(5, obj.nextDate());
            pstmt.setString(6, obj.executive());
            pstmt.setString(7, obj.remarks());
            pstmt.executeUpdate();
        }
    }

    private void insertLeadQualification(Connection conn, LeadQualification obj) throws SQLException {
        String sql = "INSERT INTO qualifications (customer, budget, loanRequired, preferredLocation, flatType, purchaseTimeline, purpose, status, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.budget());
            pstmt.setString(3, obj.loanRequired());
            pstmt.setString(4, obj.preferredLocation());
            pstmt.setString(5, obj.flatType());
            pstmt.setString(6, obj.purchaseTimeline());
            pstmt.setString(7, obj.purpose());
            pstmt.setString(8, obj.status());
            pstmt.setString(9, obj.notes());
            pstmt.executeUpdate();
        }
    }

    private void insertSiteVisit(Connection conn, SiteVisit obj) throws SQLException {
        String sql = "INSERT INTO sitevisits (customer, visitDate, visitTime, pickupRequired, executive, status, feedback) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.visitDate());
            pstmt.setString(3, obj.visitTime());
            pstmt.setString(4, obj.pickupRequired());
            pstmt.setString(5, obj.executive());
            pstmt.setString(6, obj.status());
            pstmt.setString(7, obj.feedback());
            pstmt.executeUpdate();
        }
    }

    private void insertNegotiation(Connection conn, Negotiation obj) throws SQLException {
        String sql = "INSERT INTO negotiations (customer, unit, offeredPrice, discount, managementApproval, specialOffer) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.unit());
            pstmt.setString(3, obj.offeredPrice());
            pstmt.setString(4, obj.discount());
            pstmt.setString(5, obj.managementApproval());
            pstmt.setString(6, obj.specialOffer());
            pstmt.executeUpdate();
        }
    }

    private void insertBooking(Connection conn, Booking obj) throws SQLException {
        String sql = "INSERT INTO bookings (customer, bookingAmount, bookingDate, unit, floor, flatType, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.bookingAmount());
            pstmt.setString(3, obj.bookingDate());
            pstmt.setString(4, obj.unit());
            pstmt.setString(5, obj.floor());
            pstmt.setString(6, obj.flatType());
            pstmt.setString(7, obj.status());
            pstmt.executeUpdate();
        }
    }

    private void insertDocumentCollection(Connection conn, DocumentCollection obj) throws SQLException {
        String sql = "INSERT INTO documentcollections (customer, panCard, aadhaarCard, passportPhoto, addressProof, incomeProof, bankStatement) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.panCard());
            pstmt.setString(3, obj.aadhaarCard());
            pstmt.setString(4, obj.passportPhoto());
            pstmt.setString(5, obj.addressProof());
            pstmt.setString(6, obj.incomeProof());
            pstmt.setString(7, obj.bankStatement());
            pstmt.executeUpdate();
        }
    }

    private void insertLoanDetail(Connection conn, LoanDetail obj) throws SQLException {
        String sql = "INSERT INTO loans (customer, bankName, loanAmount, emi, sanctionDate, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.bankName());
            pstmt.setString(3, obj.loanAmount());
            pstmt.setString(4, obj.emi());
            pstmt.setString(5, obj.sanctionDate());
            pstmt.setString(6, obj.status());
            pstmt.executeUpdate();
        }
    }

    private void insertAgreement(Connection conn, Agreement obj) throws SQLException {
        String sql = "INSERT INTO agreements (customer, unit, agreementDate, stampDuty, registrationDate, registrationNumber) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.unit());
            pstmt.setString(3, obj.agreementDate());
            pstmt.setString(4, obj.stampDuty());
            pstmt.setString(5, obj.registrationDate());
            pstmt.setString(6, obj.registrationNumber());
            pstmt.executeUpdate();
        }
    }

    private void insertCustomerPayment(Connection conn, CustomerPayment obj) throws SQLException {
        String sql = "INSERT INTO customerpayments (customer, installmentType, totalCost, paidAmount, pendingAmount, dueDate, history) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.installmentType());
            pstmt.setString(3, obj.totalCost());
            pstmt.setString(4, obj.paidAmount());
            pstmt.setString(5, obj.pendingAmount());
            pstmt.setString(6, obj.dueDate());
            pstmt.setString(7, obj.history());
            pstmt.executeUpdate();
        }
    }

    private void insertPossession(Connection conn, Possession obj) throws SQLException {
        String sql = "INSERT INTO possessions (customer, unit, finalInspection, utilityConnection, keyHandover, possessionLetter, status, remarks) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.unit());
            pstmt.setString(3, obj.finalInspection());
            pstmt.setString(4, obj.utilityConnection());
            pstmt.setString(5, obj.keyHandover());
            pstmt.setString(6, obj.possessionLetter());
            pstmt.setString(7, obj.status());
            pstmt.setString(8, obj.remarks());
            pstmt.executeUpdate();
        }
    }

    private void insertSupportTicket(Connection conn, SupportTicket obj) throws SQLException {
        String sql = "INSERT INTO supporttickets (customer, unit, category, priority, status, assignedTo, description) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.customer());
            pstmt.setString(2, obj.unit());
            pstmt.setString(3, obj.category());
            pstmt.setString(4, obj.priority());
            pstmt.setString(5, obj.status());
            pstmt.setString(6, obj.assignedTo());
            pstmt.setString(7, obj.description());
            pstmt.executeUpdate();
        }
    }

    private void insertEmployee(Connection conn, Employee obj) throws SQLException {
        String sql = "INSERT INTO employees (employeeId, name, department, designation, mobile, email, dateOfJoining, salaryStructure, bankDetails, identityDetails, statutoryDetails) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.employeeId());
            pstmt.setString(2, obj.name());
            pstmt.setString(3, obj.department());
            pstmt.setString(4, obj.designation());
            pstmt.setString(5, obj.mobile());
            pstmt.setString(6, obj.email());
            pstmt.setString(7, obj.dateOfJoining());
            pstmt.setString(8, obj.salaryStructure());
            pstmt.setString(9, obj.bankDetails());
            pstmt.setString(10, obj.identityDetails());
            pstmt.setString(11, obj.statutoryDetails());
            pstmt.executeUpdate();
        }
    }

    private void insertAttendance(Connection conn, Attendance obj) throws SQLException {
        String sql = "INSERT INTO attendance (employeeId, employeeName, date, status, overtimeHours, remarks) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.employeeId());
            pstmt.setString(2, obj.employeeName());
            pstmt.setString(3, obj.date());
            pstmt.setString(4, obj.status());
            pstmt.setString(5, obj.overtimeHours());
            pstmt.setString(6, obj.remarks());
            pstmt.executeUpdate();
        }
    }

    private void insertLeaveRequest(Connection conn, LeaveRequest obj) throws SQLException {
        String sql = "INSERT INTO leaverequests (employeeId, employeeName, fromDate, toDate, leaveType, status, reason) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.employeeId());
            pstmt.setString(2, obj.employeeName());
            pstmt.setString(3, obj.fromDate());
            pstmt.setString(4, obj.toDate());
            pstmt.setString(5, obj.leaveType());
            pstmt.setString(6, obj.status());
            pstmt.setString(7, obj.reason());
            pstmt.executeUpdate();
        }
    }

    private void insertSalaryPayment(Connection conn, SalaryPayment obj) throws SQLException {
        String sql = "INSERT INTO salarypayments (employeeId, employeeName, salaryMonth, basicSalary, hra, incentives, bonus, deductions, grossSalary, netSalary, paymentDate, paymentMode, paymentStatus) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.employeeId());
            pstmt.setString(2, obj.employeeName());
            pstmt.setString(3, obj.salaryMonth());
            pstmt.setString(4, obj.basicSalary());
            pstmt.setString(5, obj.hra());
            pstmt.setString(6, obj.incentives());
            pstmt.setString(7, obj.bonus());
            pstmt.setString(8, obj.deductions());
            pstmt.setString(9, obj.grossSalary());
            pstmt.setString(10, obj.netSalary());
            pstmt.setString(11, obj.paymentDate());
            pstmt.setString(12, obj.paymentMode());
            pstmt.setString(13, obj.paymentStatus());
            pstmt.executeUpdate();
        }
    }

    private void insertRegisteredUser(Connection conn, RegisteredUser obj) throws SQLException {
        String sql = upsertRegisteredUser();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.name());
            pstmt.setString(2, obj.mobile());
            pstmt.setString(3, obj.email());
            pstmt.setString(4, obj.role());
            pstmt.setString(5, obj.password());
            pstmt.setString(6, obj.createdDate());
            pstmt.executeUpdate();
        }
    }

    private void insertVendor(Connection conn, Vendor obj) throws SQLException {
        String sql = "INSERT INTO vendors (vendorName, companyName, gstNumber, panNumber, contactPerson, mobile, email, address, bankDetails, category) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.vendorName());
            pstmt.setString(2, obj.companyName());
            pstmt.setString(3, obj.gstNumber());
            pstmt.setString(4, obj.panNumber());
            pstmt.setString(5, obj.contactPerson());
            pstmt.setString(6, obj.mobile());
            pstmt.setString(7, obj.email());
            pstmt.setString(8, obj.address());
            pstmt.setString(9, obj.bankDetails());
            pstmt.setString(10, obj.category());
            pstmt.executeUpdate();
        }
    }

    private void insertPurchaseOrder(Connection conn, PurchaseOrder obj) throws SQLException {
        String sql = "INSERT INTO purchaseorders (poNumber, date, vendorName, material, quantity, rate, gst, totalAmount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.poNumber());
            pstmt.setString(2, obj.date());
            pstmt.setString(3, obj.vendorName());
            pstmt.setString(4, obj.material());
            pstmt.setString(5, obj.quantity());
            pstmt.setString(6, obj.rate());
            pstmt.setString(7, obj.gst());
            pstmt.setString(8, obj.totalAmount());
            pstmt.executeUpdate();
        }
    }

    private void insertVendorBill(Connection conn, VendorBill obj) throws SQLException {
        String sql = "INSERT INTO vendorbills (vendorName, invoiceNumber, invoiceDate, billAmount, gst, dueDate, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.vendorName());
            pstmt.setString(2, obj.invoiceNumber());
            pstmt.setString(3, obj.invoiceDate());
            pstmt.setString(4, obj.billAmount());
            pstmt.setString(5, obj.gst());
            pstmt.setString(6, obj.dueDate());
            pstmt.setString(7, obj.status());
            pstmt.executeUpdate();
        }
    }

    private void insertVendorPayment(Connection conn, VendorPayment obj) throws SQLException {
        String sql = "INSERT INTO vendorpayments (vendorName, paymentDate, paymentMode, transactionNumber, bank, paidAmount, balanceAmount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.vendorName());
            pstmt.setString(2, obj.paymentDate());
            pstmt.setString(3, obj.paymentMode());
            pstmt.setString(4, obj.transactionNumber());
            pstmt.setString(5, obj.bank());
            pstmt.setString(6, obj.paidAmount());
            pstmt.setString(7, obj.balanceAmount());
            pstmt.setString(8, obj.status());
            pstmt.executeUpdate();
        }
    }

    private void insertPettyCashEntry(Connection conn, PettyCashEntry obj) throws SQLException {
        String sql = "INSERT INTO pettycashentries (date, voucherNumber, expenseCategory, description, employeeName, amount, paymentMode, approvedBy) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.date());
            pstmt.setString(2, obj.voucherNumber());
            pstmt.setString(3, obj.expenseCategory());
            pstmt.setString(4, obj.description());
            pstmt.setString(5, obj.employeeName());
            pstmt.setString(6, obj.amount());
            pstmt.setString(7, obj.paymentMode());
            pstmt.setString(8, obj.approvedBy());
            pstmt.executeUpdate();
        }
    }

    private void insertPettyCashApproval(Connection conn, PettyCashApproval obj) throws SQLException {
        String sql = "INSERT INTO pettycashapprovals (requestedBy, approvedBy, amount, remarks, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, obj.requestedBy());
            pstmt.setString(2, obj.approvedBy());
            pstmt.setString(3, obj.amount());
            pstmt.setString(4, obj.remarks());
            pstmt.setString(5, obj.status());
            pstmt.executeUpdate();
        }
    }

    public void addEnquiry(Map<String, String> form) {
        String interest = form.getOrDefault("interestedProperty", "").trim();
        if (interest.isBlank()) {
            interest = form.getOrDefault("configuration", "").trim();
        }
        Enquiry obj = new Enquiry(
                form.getOrDefault("name", ""),
                form.getOrDefault("mobile", ""),
                form.getOrDefault("email", ""),
                form.getOrDefault("city", ""),
                form.getOrDefault("budget", ""),
                interest,
                form.getOrDefault("source", ""),
                form.getOrDefault("status", "New Lead")
        );
        try (Connection conn = openConnection()) {
            insertEnquiry(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void addProperty(Map<String, String> form) {
        PropertyUnit obj = new PropertyUnit(
                form.getOrDefault("unit", ""),
                form.getOrDefault("wing", ""),
                form.getOrDefault("floor", ""),
                form.getOrDefault("flatType", ""),
                form.getOrDefault("area", ""),
                form.getOrDefault("price", ""),
                form.getOrDefault("parking", ""),
                form.getOrDefault("availability", "Available"),
                form.getOrDefault("amenities", "")
        );
        try (Connection conn = openConnection()) {
            insertPropertyUnit(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void addFollowUp(Map<String, String> form) {
        FollowUp obj = new FollowUp(
                form.getOrDefault("customer", ""),
                form.getOrDefault("type", ""),
                form.getOrDefault("date", ""),
                form.getOrDefault("time", ""),
                form.getOrDefault("nextDate", ""),
                form.getOrDefault("executive", ""),
                form.getOrDefault("remarks", "")
        );
        try (Connection conn = openConnection()) {
            insertFollowUp(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void addQualification(Map<String, String> form) {
        LeadQualification obj = new LeadQualification(
                form.getOrDefault("customer", ""),
                form.getOrDefault("budget", ""),
                form.getOrDefault("loanRequired", ""),
                form.getOrDefault("preferredLocation", ""),
                form.getOrDefault("flatType", ""),
                form.getOrDefault("purchaseTimeline", ""),
                form.getOrDefault("purpose", ""),
                form.getOrDefault("status", "Qualified"),
                form.getOrDefault("notes", "")
        );
        try (Connection conn = openConnection()) {
            insertLeadQualification(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateEnquiry(long id, Map<String, String> form) {
        String sql = "UPDATE enquiries SET name = ?, mobile = ?, email = ?, city = ?, budget = ?, interestedProperty = ?, source = ?, status = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("name", ""));
            ps.setString(2, form.getOrDefault("mobile", ""));
            ps.setString(3, form.getOrDefault("email", ""));
            ps.setString(4, form.getOrDefault("city", ""));
            ps.setString(5, form.getOrDefault("budget", ""));
            ps.setString(6, form.getOrDefault("interestedProperty", ""));
            ps.setString(7, form.getOrDefault("source", ""));
            ps.setString(8, form.getOrDefault("status", "New Lead"));
            ps.setLong(9, id);
            requireUpdated(ps.executeUpdate(), "Enquiry not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteEnquiry(long id) {
        deleteById("enquiries", id, "Enquiry not found.");
    }

    public void updateQualification(long id, Map<String, String> form) {
        String sql = "UPDATE qualifications SET customer = ?, budget = ?, loanRequired = ?, preferredLocation = ?, flatType = ?, purchaseTimeline = ?, purpose = ?, status = ?, notes = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("budget", ""));
            ps.setString(3, form.getOrDefault("loanRequired", ""));
            ps.setString(4, form.getOrDefault("preferredLocation", ""));
            ps.setString(5, form.getOrDefault("flatType", ""));
            ps.setString(6, form.getOrDefault("purchaseTimeline", ""));
            ps.setString(7, form.getOrDefault("purpose", ""));
            ps.setString(8, form.getOrDefault("status", "Qualified"));
            ps.setString(9, form.getOrDefault("notes", ""));
            ps.setLong(10, id);
            requireUpdated(ps.executeUpdate(), "Qualification not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteQualification(long id) {
        deleteById("qualifications", id, "Qualification not found.");
    }

    public void updateFollowUp(long id, Map<String, String> form) {
        String sql = "UPDATE followups SET customer = ?, type = ?, date = ?, time = ?, nextDate = ?, executive = ?, remarks = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("type", ""));
            ps.setString(3, form.getOrDefault("date", ""));
            ps.setString(4, form.getOrDefault("time", ""));
            ps.setString(5, form.getOrDefault("nextDate", ""));
            ps.setString(6, form.getOrDefault("executive", ""));
            ps.setString(7, form.getOrDefault("remarks", ""));
            ps.setLong(8, id);
            requireUpdated(ps.executeUpdate(), "Follow-up not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteFollowUp(long id) {
        deleteById("followups", id, "Follow-up not found.");
    }

    public void updateSiteVisit(long id, Map<String, String> form) {
        String sql = "UPDATE sitevisits SET customer = ?, visitDate = ?, visitTime = ?, pickupRequired = ?, executive = ?, status = ?, feedback = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("visitDate", ""));
            ps.setString(3, form.getOrDefault("visitTime", ""));
            ps.setString(4, form.getOrDefault("pickupRequired", "No"));
            ps.setString(5, form.getOrDefault("executive", ""));
            ps.setString(6, form.getOrDefault("status", "Scheduled"));
            ps.setString(7, form.getOrDefault("feedback", ""));
            ps.setLong(8, id);
            requireUpdated(ps.executeUpdate(), "Site visit not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteSiteVisit(long id) {
        deleteById("sitevisits", id, "Site visit not found.");
    }

    public void updateProperty(long id, Map<String, String> form) {
        String sql = "UPDATE properties SET unit = ?, wing = ?, floor = ?, flatType = ?, area = ?, price = ?, parking = ?, availability = ?, amenities = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("unit", ""));
            ps.setString(2, form.getOrDefault("wing", ""));
            ps.setString(3, form.getOrDefault("floor", ""));
            ps.setString(4, form.getOrDefault("flatType", ""));
            ps.setString(5, form.getOrDefault("area", ""));
            ps.setString(6, form.getOrDefault("price", ""));
            ps.setString(7, form.getOrDefault("parking", ""));
            ps.setString(8, form.getOrDefault("availability", "Available"));
            ps.setString(9, form.getOrDefault("amenities", ""));
            ps.setLong(10, id);
            requireUpdated(ps.executeUpdate(), "Property not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteProperty(long id) {
        deleteById("properties", id, "Property not found.");
    }

    public void updateNegotiation(long id, Map<String, String> form) {
        String sql = "UPDATE negotiations SET customer = ?, unit = ?, offeredPrice = ?, discount = ?, managementApproval = ?, specialOffer = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("unit", ""));
            ps.setString(3, form.getOrDefault("offeredPrice", ""));
            ps.setString(4, form.getOrDefault("discount", ""));
            ps.setString(5, form.getOrDefault("managementApproval", "Pending"));
            ps.setString(6, form.getOrDefault("specialOffer", ""));
            ps.setLong(7, id);
            requireUpdated(ps.executeUpdate(), "Negotiation not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteNegotiation(long id) {
        deleteById("negotiations", id, "Negotiation not found.");
    }

    public void addSiteVisit(Map<String, String> form) {
        SiteVisit obj = new SiteVisit(
                form.getOrDefault("customer", ""),
                form.getOrDefault("visitDate", ""),
                form.getOrDefault("visitTime", ""),
                form.getOrDefault("pickupRequired", "No"),
                form.getOrDefault("executive", ""),
                form.getOrDefault("status", "Scheduled"),
                form.getOrDefault("feedback", "")
        );
        try (Connection conn = openConnection()) {
            insertSiteVisit(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void addNegotiation(Map<String, String> form) {
        Negotiation obj = new Negotiation(
                form.getOrDefault("customer", ""),
                form.getOrDefault("unit", ""),
                form.getOrDefault("offeredPrice", ""),
                form.getOrDefault("discount", ""),
                form.getOrDefault("managementApproval", "Pending"),
                form.getOrDefault("specialOffer", "")
        );
        try (Connection conn = openConnection()) {
            insertNegotiation(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void addBooking(Map<String, String> form) {
        Booking obj = new Booking(
                form.getOrDefault("customer", ""),
                form.getOrDefault("bookingAmount", ""),
                form.getOrDefault("bookingDate", ""),
                form.getOrDefault("unit", ""),
                form.getOrDefault("floor", ""),
                form.getOrDefault("flatType", ""),
                form.getOrDefault("status", "Pending")
        );
        try (Connection conn = openConnection()) {
            insertBooking(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateBooking(long id, Map<String, String> form) {
        String sql = "UPDATE bookings SET customer = ?, bookingAmount = ?, bookingDate = ?, unit = ?, floor = ?, flatType = ?, status = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("bookingAmount", ""));
            ps.setString(3, form.getOrDefault("bookingDate", ""));
            ps.setString(4, form.getOrDefault("unit", ""));
            ps.setString(5, form.getOrDefault("floor", ""));
            ps.setString(6, form.getOrDefault("flatType", ""));
            ps.setString(7, form.getOrDefault("status", "Pending"));
            ps.setLong(8, id);
            requireUpdated(ps.executeUpdate(), "Booking not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteBooking(long id) {
        deleteById("bookings", id, "Booking not found.");
    }

    public void addDocumentCollection(Map<String, String> form) {
        DocumentCollection obj = new DocumentCollection(
                form.getOrDefault("customer", ""),
                form.getOrDefault("panCard", ""),
                form.getOrDefault("aadhaarCard", ""),
                form.getOrDefault("passportPhoto", ""),
                form.getOrDefault("addressProof", ""),
                form.getOrDefault("incomeProof", ""),
                form.getOrDefault("bankStatement", "")
        );
        try (Connection conn = openConnection()) {
            insertDocumentCollection(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateDocumentCollection(long id, Map<String, String> form) {
        String sql = "UPDATE documentcollections SET customer = ?, panCard = ?, aadhaarCard = ?, passportPhoto = ?, addressProof = ?, incomeProof = ?, bankStatement = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("panCard", ""));
            ps.setString(3, form.getOrDefault("aadhaarCard", ""));
            ps.setString(4, form.getOrDefault("passportPhoto", ""));
            ps.setString(5, form.getOrDefault("addressProof", ""));
            ps.setString(6, form.getOrDefault("incomeProof", ""));
            ps.setString(7, form.getOrDefault("bankStatement", ""));
            ps.setLong(8, id);
            requireUpdated(ps.executeUpdate(), "Document collection not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteDocumentCollection(long id) {
        deleteById("documentcollections", id, "Document collection not found.");
    }

    public void addLoan(Map<String, String> form) {
        LoanDetail obj = new LoanDetail(
                form.getOrDefault("customer", ""),
                form.getOrDefault("bankName", ""),
                form.getOrDefault("loanAmount", ""),
                form.getOrDefault("emi", ""),
                form.getOrDefault("sanctionDate", ""),
                form.getOrDefault("status", "Loan Applied")
        );
        try (Connection conn = openConnection()) {
            insertLoanDetail(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateLoan(long id, Map<String, String> form) {
        String sql = "UPDATE loans SET customer = ?, bankName = ?, loanAmount = ?, emi = ?, sanctionDate = ?, status = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("bankName", ""));
            ps.setString(3, form.getOrDefault("loanAmount", ""));
            ps.setString(4, form.getOrDefault("emi", ""));
            ps.setString(5, form.getOrDefault("sanctionDate", ""));
            ps.setString(6, form.getOrDefault("status", "Loan Applied"));
            ps.setLong(7, id);
            requireUpdated(ps.executeUpdate(), "Loan not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteLoan(long id) {
        deleteById("loans", id, "Loan not found.");
    }

    public void addAgreement(Map<String, String> form) {
        Agreement obj = new Agreement(
                form.getOrDefault("customer", ""),
                form.getOrDefault("unit", ""),
                form.getOrDefault("agreementDate", ""),
                form.getOrDefault("stampDuty", ""),
                form.getOrDefault("registrationDate", ""),
                form.getOrDefault("registrationNumber", "")
        );
        try (Connection conn = openConnection()) {
            insertAgreement(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateAgreement(long id, Map<String, String> form) {
        String sql = "UPDATE agreements SET customer = ?, unit = ?, agreementDate = ?, stampDuty = ?, registrationDate = ?, registrationNumber = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("unit", ""));
            ps.setString(3, form.getOrDefault("agreementDate", ""));
            ps.setString(4, form.getOrDefault("stampDuty", ""));
            ps.setString(5, form.getOrDefault("registrationDate", ""));
            ps.setString(6, form.getOrDefault("registrationNumber", ""));
            ps.setLong(7, id);
            requireUpdated(ps.executeUpdate(), "Agreement not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteAgreement(long id) {
        deleteById("agreements", id, "Agreement not found.");
    }

    public void addCustomerPayment(Map<String, String> form) {
        String total = form.getOrDefault("totalCost", "");
        String paid = form.getOrDefault("paidAmount", "");
        String pending = form.getOrDefault("pendingAmount", "");
        if (pending.isBlank() && !total.isBlank() && !paid.isBlank()) {
            pending = String.valueOf(money(form, "totalCost") - money(form, "paidAmount"));
        } else if (paid.isBlank() && !total.isBlank() && !pending.isBlank()) {
            paid = String.valueOf(money(form, "totalCost") - money(form, "pendingAmount"));
        }
        CustomerPayment obj = new CustomerPayment(
                form.getOrDefault("customer", ""),
                form.getOrDefault("installmentType", ""),
                total,
                paid,
                pending,
                form.getOrDefault("dueDate", ""),
                form.getOrDefault("history", "")
        );
        try (Connection conn = openConnection()) {
            insertCustomerPayment(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateCustomerPayment(long id, Map<String, String> form) {
        String total = form.getOrDefault("totalCost", "");
        String paid = form.getOrDefault("paidAmount", "");
        String pending = form.getOrDefault("pendingAmount", "");
        
        // Auto-calculate missing values
        if (pending.isBlank() && !total.isBlank() && !paid.isBlank()) {
            pending = String.valueOf(money(form, "totalCost") - money(form, "paidAmount"));
        } else if (paid.isBlank() && !total.isBlank() && !pending.isBlank()) {
            paid = String.valueOf(money(form, "totalCost") - money(form, "pendingAmount"));
        }
        
        String sql = "UPDATE customerpayments SET customer = ?, installmentType = ?, totalCost = ?, paidAmount = ?, pendingAmount = ?, dueDate = ?, history = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("installmentType", ""));
            ps.setString(3, total);
            ps.setString(4, paid);
            ps.setString(5, pending);
            ps.setString(6, form.getOrDefault("dueDate", ""));
            ps.setString(7, form.getOrDefault("history", ""));
            ps.setLong(8, id);
            requireUpdated(ps.executeUpdate(), "Sales entry not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteCustomerPayment(long id) {
        deleteById("customerpayments", id, "Sales entry not found.");
    }

    private void deleteById(String table, long id, String missingMessage) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
            ps.setLong(1, id);
            requireUpdated(ps.executeUpdate(), missingMessage);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    private void requireUpdated(int rows, String missingMessage) {
        if (rows == 0) {
            throw new IllegalArgumentException(missingMessage);
        }
    }

    public void addPossession(Map<String, String> form) {
        Possession obj = new Possession(
                form.getOrDefault("customer", ""),
                form.getOrDefault("unit", ""),
                form.getOrDefault("finalInspection", "No"),
                form.getOrDefault("utilityConnection", "No"),
                form.getOrDefault("keyHandover", "No"),
                form.getOrDefault("possessionLetter", "No"),
                form.getOrDefault("status", "Ready"),
                form.getOrDefault("remarks", "")
        );
        try (Connection conn = openConnection()) {
            insertPossession(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updatePossession(long id, Map<String, String> form) {
        String sql = "UPDATE possessions SET customer = ?, unit = ?, finalInspection = ?, utilityConnection = ?, keyHandover = ?, possessionLetter = ?, status = ?, remarks = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("unit", ""));
            ps.setString(3, form.getOrDefault("finalInspection", "No"));
            ps.setString(4, form.getOrDefault("utilityConnection", "No"));
            ps.setString(5, form.getOrDefault("keyHandover", "No"));
            ps.setString(6, form.getOrDefault("possessionLetter", "No"));
            ps.setString(7, form.getOrDefault("status", "Ready"));
            ps.setString(8, form.getOrDefault("remarks", ""));
            ps.setLong(9, id);
            requireUpdated(ps.executeUpdate(), "Possession not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deletePossession(long id) {
        deleteById("possessions", id, "Possession not found.");
    }

    public void addSupportTicket(Map<String, String> form) {
        SupportTicket obj = new SupportTicket(
                form.getOrDefault("customer", ""),
                form.getOrDefault("unit", ""),
                form.getOrDefault("category", ""),
                form.getOrDefault("priority", "Normal"),
                form.getOrDefault("status", "Open"),
                form.getOrDefault("assignedTo", ""),
                form.getOrDefault("description", "")
        );
        try (Connection conn = openConnection()) {
            insertSupportTicket(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateSupportTicket(long id, Map<String, String> form) {
        String sql = "UPDATE supporttickets SET customer = ?, unit = ?, category = ?, priority = ?, status = ?, assignedTo = ?, description = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("customer", ""));
            ps.setString(2, form.getOrDefault("unit", ""));
            ps.setString(3, form.getOrDefault("category", ""));
            ps.setString(4, form.getOrDefault("priority", "Normal"));
            ps.setString(5, form.getOrDefault("status", "Open"));
            ps.setString(6, form.getOrDefault("assignedTo", ""));
            ps.setString(7, form.getOrDefault("description", ""));
            ps.setLong(8, id);
            requireUpdated(ps.executeUpdate(), "Support ticket not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteSupportTicket(long id) {
        deleteById("supporttickets", id, "Support ticket not found.");
    }

    public void addEmployee(Map<String, String> form) {
        Employee obj = new Employee(
                form.getOrDefault("employeeId", ""),
                form.getOrDefault("name", ""),
                form.getOrDefault("department", ""),
                form.getOrDefault("designation", ""),
                form.getOrDefault("mobile", ""),
                form.getOrDefault("email", ""),
                form.getOrDefault("dateOfJoining", ""),
                form.getOrDefault("salaryStructure", ""),
                form.getOrDefault("bankDetails", ""),
                form.getOrDefault("identityDetails", ""),
                form.getOrDefault("statutoryDetails", "")
        );
        try (Connection conn = openConnection()) {
            insertEmployee(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateEmployee(long id, Map<String, String> form) {
        String sql = "UPDATE employees SET employeeId = ?, name = ?, department = ?, designation = ?, mobile = ?, email = ?, dateOfJoining = ?, salaryStructure = ?, bankDetails = ?, identityDetails = ?, statutoryDetails = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("employeeId", ""));
            ps.setString(2, form.getOrDefault("name", ""));
            ps.setString(3, form.getOrDefault("department", ""));
            ps.setString(4, form.getOrDefault("designation", ""));
            ps.setString(5, form.getOrDefault("mobile", ""));
            ps.setString(6, form.getOrDefault("email", ""));
            ps.setString(7, form.getOrDefault("dateOfJoining", ""));
            ps.setString(8, form.getOrDefault("salaryStructure", ""));
            ps.setString(9, form.getOrDefault("bankDetails", ""));
            ps.setString(10, form.getOrDefault("identityDetails", ""));
            ps.setString(11, form.getOrDefault("statutoryDetails", ""));
            ps.setLong(12, id);
            requireUpdated(ps.executeUpdate(), "Employee not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteEmployee(long id) {
        deleteById("employees", id, "Employee not found.");
    }

    public void addAttendance(Map<String, String> form) {
        Attendance obj = new Attendance(
                form.getOrDefault("employeeId", ""),
                form.getOrDefault("employeeName", ""),
                form.getOrDefault("date", ""),
                form.getOrDefault("status", "Present"),
                form.getOrDefault("overtimeHours", "0"),
                form.getOrDefault("remarks", "")
        );
        try (Connection conn = openConnection()) {
            insertAttendance(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateAttendance(long id, Map<String, String> form) {
        String sql = "UPDATE attendance SET employeeId = ?, employeeName = ?, date = ?, status = ?, overtimeHours = ?, remarks = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("employeeId", ""));
            ps.setString(2, form.getOrDefault("employeeName", ""));
            ps.setString(3, form.getOrDefault("date", ""));
            ps.setString(4, form.getOrDefault("status", "Present"));
            ps.setString(5, form.getOrDefault("overtimeHours", "0"));
            ps.setString(6, form.getOrDefault("remarks", ""));
            ps.setLong(7, id);
            requireUpdated(ps.executeUpdate(), "Attendance not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteAttendance(long id) {
        deleteById("attendance", id, "Attendance not found.");
    }

    public void addLeaveRequest(Map<String, String> form) {
        LeaveRequest obj = new LeaveRequest(
                form.getOrDefault("employeeId", ""),
                form.getOrDefault("employeeName", ""),
                form.getOrDefault("fromDate", ""),
                form.getOrDefault("toDate", ""),
                form.getOrDefault("leaveType", ""),
                form.getOrDefault("status", "Pending"),
                form.getOrDefault("reason", "")
        );
        try (Connection conn = openConnection()) {
            insertLeaveRequest(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateLeaveRequest(long id, Map<String, String> form) {
        String sql = "UPDATE leaverequests SET employeeId = ?, employeeName = ?, fromDate = ?, toDate = ?, leaveType = ?, status = ?, reason = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("employeeId", ""));
            ps.setString(2, form.getOrDefault("employeeName", ""));
            ps.setString(3, form.getOrDefault("fromDate", ""));
            ps.setString(4, form.getOrDefault("toDate", ""));
            ps.setString(5, form.getOrDefault("leaveType", ""));
            ps.setString(6, form.getOrDefault("status", "Pending"));
            ps.setString(7, form.getOrDefault("reason", ""));
            ps.setLong(8, id);
            requireUpdated(ps.executeUpdate(), "Leave request not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteLeaveRequest(long id) {
        deleteById("leaverequests", id, "Leave request not found.");
    }

    public void addSalaryPayment(Map<String, String> form) {
        String basic = form.getOrDefault("basicSalary", "");
        String hra = form.getOrDefault("hra", "");
        String incentives = form.getOrDefault("incentives", "");
        String bonus = form.getOrDefault("bonus", "");
        String deductions = form.getOrDefault("deductions", "");
        String gross = form.getOrDefault("grossSalary", "");
        String net = form.getOrDefault("netSalary", "");
        
        long b = money(form, "basicSalary");
        long h = money(form, "hra");
        long i = money(form, "incentives");
        long bo = money(form, "bonus");
        long d = money(form, "deductions");
        
        if (gross.isBlank()) {
            gross = String.valueOf(b + h + i + bo);
        }
        if (net.isBlank() && !gross.isBlank() && !deductions.isBlank()) {
            net = String.valueOf(Long.parseLong(gross) - d);
        } else if (deductions.isBlank() && !gross.isBlank() && !net.isBlank()) {
            deductions = String.valueOf(Long.parseLong(gross) - Long.parseLong(net));
        }
        
        SalaryPayment obj = new SalaryPayment(
                form.getOrDefault("employeeId", ""),
                form.getOrDefault("employeeName", ""),
                form.getOrDefault("salaryMonth", ""),
                basic,
                hra,
                incentives,
                bonus,
                deductions,
                gross,
                net,
                form.getOrDefault("paymentDate", ""),
                form.getOrDefault("paymentMode", ""),
                form.getOrDefault("paymentStatus", "")
        );
        try (Connection conn = openConnection()) {
            insertSalaryPayment(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateSalaryPayment(long id, Map<String, String> form) {
        String basic = form.getOrDefault("basicSalary", "");
        String hra = form.getOrDefault("hra", "");
        String incentives = form.getOrDefault("incentives", "");
        String bonus = form.getOrDefault("bonus", "");
        String deductions = form.getOrDefault("deductions", "");
        String gross = form.getOrDefault("grossSalary", "");
        String net = form.getOrDefault("netSalary", "");
        
        long b = money(form, "basicSalary");
        long h = money(form, "hra");
        long i = money(form, "incentives");
        long bo = money(form, "bonus");
        long d = money(form, "deductions");
        
        if (gross.isBlank()) {
            gross = String.valueOf(b + h + i + bo);
        }
        if (net.isBlank() && !gross.isBlank() && !deductions.isBlank()) {
            net = String.valueOf(Long.parseLong(gross) - d);
        } else if (deductions.isBlank() && !gross.isBlank() && !net.isBlank()) {
            deductions = String.valueOf(Long.parseLong(gross) - Long.parseLong(net));
        }
        
        String sql = "UPDATE salarypayments SET employeeId = ?, employeeName = ?, salaryMonth = ?, basicSalary = ?, hra = ?, incentives = ?, bonus = ?, deductions = ?, grossSalary = ?, netSalary = ?, paymentDate = ?, paymentMode = ?, paymentStatus = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("employeeId", ""));
            ps.setString(2, form.getOrDefault("employeeName", ""));
            ps.setString(3, form.getOrDefault("salaryMonth", ""));
            ps.setString(4, basic);
            ps.setString(5, hra);
            ps.setString(6, incentives);
            ps.setString(7, bonus);
            ps.setString(8, deductions);
            ps.setString(9, gross);
            ps.setString(10, net);
            ps.setString(11, form.getOrDefault("paymentDate", ""));
            ps.setString(12, form.getOrDefault("paymentMode", ""));
            ps.setString(13, form.getOrDefault("paymentStatus", ""));
            ps.setLong(14, id);
            requireUpdated(ps.executeUpdate(), "Salary payment not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteSalaryPayment(long id) {
        deleteById("salarypayments", id, "Salary payment not found.");
    }

    /**
     * Salted SHA-256 (format sha256$salt$hash). Old "sha256:…" and plaintext
     * rows keep working and are upgraded on next successful login.
     */
    public static String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return hashWithSalt(password, Base64.getEncoder().encodeToString(salt));
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing is unavailable.", e);
        }
    }

    private static String hashWithSalt(String password, String saltBase64) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(saltBase64));
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return "sha256$" + saltBase64 + "$" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing is unavailable.", e);
        }
    }

    private static boolean passwordMatches(String input, String stored) {
        if (stored == null) {
            return false;
        }
        if (stored.startsWith("sha256$")) {
            String[] parts = stored.split("\\$", 3);
            if (parts.length == 3) {
                return hashWithSalt(input, parts[1]).equals(stored);
            }
            return false;
        }
        if (stored.startsWith("sha256:")) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                String legacy = "sha256:" + Base64.getEncoder().encodeToString(
                        md.digest(input.getBytes(StandardCharsets.UTF_8)));
                return legacy.equals(stored);
            } catch (Exception e) {
                return false;
            }
        }
        return input.equals(stored);
    }

    private static long money(Map<String, String> form, String key) {
        String v = form.getOrDefault(key, "");
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(v.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double decimal(Map<String, String> form, String key) {
        String v = form.getOrDefault(key, "");
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(v.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void addRegisteredUser(Map<String, String> form) {
        String role = RolePolicy.normalize(form.getOrDefault("role", RolePolicy.EMPLOYEE));
        if (!RolePolicy.EMPLOYEE.equals(role) && !RolePolicy.AGENT.equals(role)) {
            throw new IllegalArgumentException("Only EMPLOYEE or AGENT users can be created.");
        }
        RegisteredUser obj = new RegisteredUser(
                form.getOrDefault("name", ""),
                form.getOrDefault("mobile", ""),
                form.getOrDefault("email", "").trim().toLowerCase(),
                role,
                hashPassword(form.getOrDefault("password", "")),
                LocalDate.now().toString()
        );
        Connection conn = null;
        try {
            conn = openConnection();
            conn.setAutoCommit(false);
            insertRegisteredUser(conn, obj);
            if (RolePolicy.EMPLOYEE.equals(role)) {
                ensureEmployeeRecord(conn, obj.name(), obj.mobile(), obj.email());
            }
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    rollbackError.printStackTrace(System.err);
                }
            }
            e.printStackTrace(); throw mapSaveError(e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException closeError) {
                    closeError.printStackTrace(System.err);
                }
            }
        }
    }

    public String authenticateRegisteredUser(String email, String password, String selectedRole) {
        selectedRole = RolePolicy.normalize(selectedRole);
        String sql = "SELECT role, password FROM registeredusers WHERE lower(email) = ? AND role = ?";
        try (Connection conn = openConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email.trim().toLowerCase());
            pstmt.setString(2, selectedRole);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("password");
                    if (passwordMatches(password, stored)) {
                        // Transparently upgrade legacy rows to salted hashes.
                        if (stored == null || !stored.startsWith("sha256$")) {
                            try (PreparedStatement upd = conn.prepareStatement(
                                    "UPDATE registeredusers SET password = ? WHERE lower(email) = ? AND role = ?")) {
                                upd.setString(1, hashPassword(password));
                                upd.setString(2, email.trim().toLowerCase());
                                upd.setString(3, selectedRole);
                                upd.executeUpdate();
                            } catch (SQLException ignored) {
                            }
                        }
                        return RolePolicy.normalize(rs.getString("role"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return null;
    }

    public int countUsersByRole(String role) {
        int count = 0;
        for (RegisteredUser user : getRegisteredUsers()) {
            if (RolePolicy.normalize(user.role()).equals(role)) {
                count++;
            }
        }
        return count;
    }

    public void addVendor(Map<String, String> form) {
        Vendor obj = new Vendor(form.getOrDefault("vendorName", ""), form.getOrDefault("companyName", ""), form.getOrDefault("gstNumber", ""), form.getOrDefault("panNumber", ""), form.getOrDefault("contactPerson", ""), form.getOrDefault("mobile", ""), form.getOrDefault("email", ""), form.getOrDefault("address", ""), form.getOrDefault("bankDetails", ""), form.getOrDefault("category", ""));
        try (Connection conn = openConnection()) {
            insertVendor(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateVendor(long id, Map<String, String> form) {
        String sql = "UPDATE vendors SET vendorName = ?, companyName = ?, gstNumber = ?, panNumber = ?, contactPerson = ?, mobile = ?, email = ?, address = ?, bankDetails = ?, category = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("vendorName", ""));
            ps.setString(2, form.getOrDefault("companyName", ""));
            ps.setString(3, form.getOrDefault("gstNumber", ""));
            ps.setString(4, form.getOrDefault("panNumber", ""));
            ps.setString(5, form.getOrDefault("contactPerson", ""));
            ps.setString(6, form.getOrDefault("mobile", ""));
            ps.setString(7, form.getOrDefault("email", ""));
            ps.setString(8, form.getOrDefault("address", ""));
            ps.setString(9, form.getOrDefault("bankDetails", ""));
            ps.setString(10, form.getOrDefault("category", ""));
            ps.setLong(11, id);
            requireUpdated(ps.executeUpdate(), "Vendor not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteVendor(long id) {
        deleteById("vendors", id, "Vendor not found.");
    }

    public void addPurchaseOrder(Map<String, String> form) {
        String qty = form.getOrDefault("quantity", "");
        String rate = form.getOrDefault("rate", "");
        String gst = form.getOrDefault("gst", "");
        String total = form.getOrDefault("totalAmount", "");
        
        if (total.isBlank() && !qty.isBlank() && !rate.isBlank()) {
            double q = decimal(form, "quantity");
            double r = decimal(form, "rate");
            double gstPercent = 0;
            if (!gst.isBlank()) {
                try {
                    gstPercent = Double.parseDouble(gst.replaceAll("[^0-9.]", ""));
                } catch (NumberFormatException ignored) {}
            }
            double base = q * r;
            double totalWithGst = base + (base * gstPercent / 100.0);
            total = String.valueOf((long) Math.round(totalWithGst));
        }
        
        PurchaseOrder obj = new PurchaseOrder(
                form.getOrDefault("poNumber", ""),
                form.getOrDefault("date", ""),
                form.getOrDefault("vendorName", ""),
                form.getOrDefault("material", ""),
                qty,
                rate,
                gst,
                total
        );
        try (Connection conn = openConnection()) {
            insertPurchaseOrder(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updatePurchaseOrder(long id, Map<String, String> form) {
        String qty = form.getOrDefault("quantity", "");
        String rate = form.getOrDefault("rate", "");
        String gst = form.getOrDefault("gst", "");
        String total = form.getOrDefault("totalAmount", "");
        
        if (total.isBlank() && !qty.isBlank() && !rate.isBlank()) {
            double q = decimal(form, "quantity");
            double r = decimal(form, "rate");
            double gstPercent = 0;
            if (!gst.isBlank()) {
                try {
                    gstPercent = Double.parseDouble(gst.replaceAll("[^0-9.]", ""));
                } catch (NumberFormatException ignored) {}
            }
            double base = q * r;
            double totalWithGst = base + (base * gstPercent / 100.0);
            total = String.valueOf((long) Math.round(totalWithGst));
        }
        
        String sql = "UPDATE purchaseorders SET poNumber = ?, date = ?, vendorName = ?, material = ?, quantity = ?, rate = ?, gst = ?, totalAmount = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("poNumber", ""));
            ps.setString(2, form.getOrDefault("date", ""));
            ps.setString(3, form.getOrDefault("vendorName", ""));
            ps.setString(4, form.getOrDefault("material", ""));
            ps.setString(5, qty);
            ps.setString(6, rate);
            ps.setString(7, gst);
            ps.setString(8, total);
            ps.setLong(9, id);
            requireUpdated(ps.executeUpdate(), "Purchase order not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deletePurchaseOrder(long id) {
        deleteById("purchaseorders", id, "Purchase order not found.");
    }

    public void addVendorBill(Map<String, String> form) {
        VendorBill obj = new VendorBill(form.getOrDefault("vendorName", ""), form.getOrDefault("invoiceNumber", ""), form.getOrDefault("invoiceDate", ""), form.getOrDefault("billAmount", ""), form.getOrDefault("gst", ""), form.getOrDefault("dueDate", ""), form.getOrDefault("status", "Pending"));
        try (Connection conn = openConnection()) {
            insertVendorBill(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateVendorBill(long id, Map<String, String> form) {
        String sql = "UPDATE vendorbills SET vendorName = ?, invoiceNumber = ?, invoiceDate = ?, billAmount = ?, gst = ?, dueDate = ?, status = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("vendorName", ""));
            ps.setString(2, form.getOrDefault("invoiceNumber", ""));
            ps.setString(3, form.getOrDefault("invoiceDate", ""));
            ps.setString(4, form.getOrDefault("billAmount", ""));
            ps.setString(5, form.getOrDefault("gst", ""));
            ps.setString(6, form.getOrDefault("dueDate", ""));
            ps.setString(7, form.getOrDefault("status", "Pending"));
            ps.setLong(8, id);
            requireUpdated(ps.executeUpdate(), "Vendor bill not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteVendorBill(long id) {
        deleteById("vendorbills", id, "Vendor bill not found.");
    }

    public void addVendorPayment(Map<String, String> form) {
        VendorPayment obj = new VendorPayment(form.getOrDefault("vendorName", ""), form.getOrDefault("paymentDate", ""), form.getOrDefault("paymentMode", ""), form.getOrDefault("transactionNumber", ""), form.getOrDefault("bank", ""), form.getOrDefault("paidAmount", ""), form.getOrDefault("balanceAmount", ""), form.getOrDefault("status", "Pending"));
        try (Connection conn = openConnection()) {
            insertVendorPayment(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updateVendorPayment(long id, Map<String, String> form) {
        String sql = "UPDATE vendorpayments SET vendorName = ?, paymentDate = ?, paymentMode = ?, transactionNumber = ?, bank = ?, paidAmount = ?, balanceAmount = ?, status = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("vendorName", ""));
            ps.setString(2, form.getOrDefault("paymentDate", ""));
            ps.setString(3, form.getOrDefault("paymentMode", ""));
            ps.setString(4, form.getOrDefault("transactionNumber", ""));
            ps.setString(5, form.getOrDefault("bank", ""));
            ps.setString(6, form.getOrDefault("paidAmount", ""));
            ps.setString(7, form.getOrDefault("balanceAmount", ""));
            ps.setString(8, form.getOrDefault("status", "Pending"));
            ps.setLong(9, id);
            requireUpdated(ps.executeUpdate(), "Vendor payment not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deleteVendorPayment(long id) {
        deleteById("vendorpayments", id, "Vendor payment not found.");
    }

    public void addPettyCashEntry(Map<String, String> form) {
        PettyCashEntry obj = new PettyCashEntry(form.getOrDefault("date", ""), form.getOrDefault("voucherNumber", ""), form.getOrDefault("expenseCategory", ""), form.getOrDefault("description", ""), form.getOrDefault("employeeName", ""), form.getOrDefault("amount", ""), form.getOrDefault("paymentMode", ""), form.getOrDefault("approvedBy", ""));
        try (Connection conn = openConnection()) {
            insertPettyCashEntry(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updatePettyCashEntry(long id, Map<String, String> form) {
        String sql = "UPDATE pettycashentries SET date = ?, voucherNumber = ?, expenseCategory = ?, description = ?, employeeName = ?, amount = ?, paymentMode = ?, approvedBy = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("date", ""));
            ps.setString(2, form.getOrDefault("voucherNumber", ""));
            ps.setString(3, form.getOrDefault("expenseCategory", ""));
            ps.setString(4, form.getOrDefault("description", ""));
            ps.setString(5, form.getOrDefault("employeeName", ""));
            ps.setString(6, form.getOrDefault("amount", ""));
            ps.setString(7, form.getOrDefault("paymentMode", ""));
            ps.setString(8, form.getOrDefault("approvedBy", ""));
            ps.setLong(9, id);
            requireUpdated(ps.executeUpdate(), "Petty cash entry not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deletePettyCashEntry(long id) {
        deleteById("pettycashentries", id, "Petty cash entry not found.");
    }

    public void addPettyCashApproval(Map<String, String> form) {
        PettyCashApproval obj = new PettyCashApproval(form.getOrDefault("requestedBy", ""), form.getOrDefault("approvedBy", ""), form.getOrDefault("amount", ""), form.getOrDefault("remarks", ""), form.getOrDefault("status", "Pending"));
        try (Connection conn = openConnection()) {
            insertPettyCashApproval(conn, obj);
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void updatePettyCashApproval(long id, Map<String, String> form) {
        String sql = "UPDATE pettycashapprovals SET requestedBy = ?, approvedBy = ?, amount = ?, remarks = ?, status = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.getOrDefault("requestedBy", ""));
            ps.setString(2, form.getOrDefault("approvedBy", ""));
            ps.setString(3, form.getOrDefault("amount", ""));
            ps.setString(4, form.getOrDefault("remarks", ""));
            ps.setString(5, form.getOrDefault("status", "Pending"));
            ps.setLong(6, id);
            requireUpdated(ps.executeUpdate(), "Petty cash approval not found.");
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
    }

    public void deletePettyCashApproval(long id) {
        deleteById("pettycashapprovals", id, "Petty cash approval not found.");
    }

    public List<Enquiry> getEnquiries() {
        List<Enquiry> list = new ArrayList<>();
        String sql = "SELECT * FROM enquiries";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Enquiry(
                    rs.getLong("id"), rs.getString("name"), rs.getString("mobile"), rs.getString("email"), rs.getString("city"), rs.getString("budget"), rs.getString("interestedProperty"), rs.getString("source"), rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<PropertyUnit> getProperties() {
        List<PropertyUnit> list = new ArrayList<>();
        String sql = "SELECT * FROM properties";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new PropertyUnit(
                    rs.getLong("id"), rs.getString("unit"), rs.getString("wing"), rs.getString("floor"), rs.getString("flatType"), rs.getString("area"), rs.getString("price"), rs.getString("parking"), rs.getString("availability"), rs.getString("amenities")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<FollowUp> getFollowUps() {
        List<FollowUp> list = new ArrayList<>();
        String sql = "SELECT * FROM followups";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new FollowUp(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("type"), rs.getString("date"), rs.getString("time"), rs.getString("nextDate"), rs.getString("executive"), rs.getString("remarks")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<LeadQualification> getQualifications() {
        List<LeadQualification> list = new ArrayList<>();
        String sql = "SELECT * FROM qualifications";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new LeadQualification(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("budget"), rs.getString("loanRequired"), rs.getString("preferredLocation"), rs.getString("flatType"), rs.getString("purchaseTimeline"), rs.getString("purpose"), rs.getString("status"), rs.getString("notes")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<SiteVisit> getSiteVisits() {
        List<SiteVisit> list = new ArrayList<>();
        String sql = "SELECT * FROM sitevisits";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new SiteVisit(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("visitDate"), rs.getString("visitTime"), rs.getString("pickupRequired"), rs.getString("executive"), rs.getString("status"), rs.getString("feedback")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<Negotiation> getNegotiations() {
        List<Negotiation> list = new ArrayList<>();
        String sql = "SELECT * FROM negotiations";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Negotiation(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("unit"), rs.getString("offeredPrice"), rs.getString("discount"), rs.getString("managementApproval"), rs.getString("specialOffer")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<Booking> getBookings() {
        List<Booking> list = new ArrayList<>();
        String sql = "SELECT * FROM bookings";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Booking(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("bookingAmount"), rs.getString("bookingDate"), rs.getString("unit"), rs.getString("floor"), rs.getString("flatType"), rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<DocumentCollection> getDocumentCollections() {
        List<DocumentCollection> list = new ArrayList<>();
        String sql = "SELECT * FROM documentcollections";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new DocumentCollection(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("panCard"), rs.getString("aadhaarCard"), rs.getString("passportPhoto"), rs.getString("addressProof"), rs.getString("incomeProof"), rs.getString("bankStatement")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<LoanDetail> getLoans() {
        List<LoanDetail> list = new ArrayList<>();
        String sql = "SELECT * FROM loans";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new LoanDetail(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("bankName"), rs.getString("loanAmount"), rs.getString("emi"), rs.getString("sanctionDate"), rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<Agreement> getAgreements() {
        List<Agreement> list = new ArrayList<>();
        String sql = "SELECT * FROM agreements";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Agreement(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("unit"), rs.getString("agreementDate"), rs.getString("stampDuty"), rs.getString("registrationDate"), rs.getString("registrationNumber")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<CustomerPayment> getCustomerPayments() {
        List<CustomerPayment> list = new ArrayList<>();
        String sql = "SELECT * FROM customerpayments";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new CustomerPayment(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("installmentType"), rs.getString("totalCost"), rs.getString("paidAmount"), rs.getString("pendingAmount"), rs.getString("dueDate"), rs.getString("history")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<Possession> getPossessions() {
        List<Possession> list = new ArrayList<>();
        String sql = "SELECT * FROM possessions";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Possession(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("unit"), rs.getString("finalInspection"), rs.getString("utilityConnection"), rs.getString("keyHandover"), rs.getString("possessionLetter"), rs.getString("status"), rs.getString("remarks")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<SupportTicket> getSupportTickets() {
        List<SupportTicket> list = new ArrayList<>();
        String sql = "SELECT * FROM supporttickets";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new SupportTicket(
                    rs.getLong("id"), rs.getString("customer"), rs.getString("unit"), rs.getString("category"), rs.getString("priority"), rs.getString("status"), rs.getString("assignedTo"), rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<Employee> getEmployees() {
        List<Employee> list = new ArrayList<>();
        String sql = "SELECT * FROM employees";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Employee(
                    rs.getLong("id"), rs.getString("employeeId"), rs.getString("name"), rs.getString("department"), rs.getString("designation"), rs.getString("mobile"), rs.getString("email"), rs.getString("dateOfJoining"), rs.getString("salaryStructure"), rs.getString("bankDetails"), rs.getString("identityDetails"), rs.getString("statutoryDetails")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<Attendance> getAttendance() {
        List<Attendance> list = new ArrayList<>();
        String sql = "SELECT * FROM attendance";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Attendance(
                    rs.getLong("id"), rs.getString("employeeId"), rs.getString("employeeName"), rs.getString("date"), rs.getString("status"), rs.getString("overtimeHours"), rs.getString("remarks")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<LeaveRequest> getLeaveRequests() {
        List<LeaveRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM leaverequests";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new LeaveRequest(
                    rs.getLong("id"), rs.getString("employeeId"), rs.getString("employeeName"), rs.getString("fromDate"), rs.getString("toDate"), rs.getString("leaveType"), rs.getString("status"), rs.getString("reason")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<SalaryPayment> getSalaryPayments() {
        List<SalaryPayment> list = new ArrayList<>();
        String sql = "SELECT * FROM salarypayments";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new SalaryPayment(
                    rs.getLong("id"), rs.getString("employeeId"), rs.getString("employeeName"), rs.getString("salaryMonth"), rs.getString("basicSalary"), rs.getString("hra"), rs.getString("incentives"), rs.getString("bonus"), rs.getString("deductions"), rs.getString("grossSalary"), rs.getString("netSalary"), rs.getString("paymentDate"), rs.getString("paymentMode"), rs.getString("paymentStatus")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<Vendor> getVendors() {
        List<Vendor> list = new ArrayList<>();
        String sql = "SELECT * FROM vendors";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Vendor(rs.getLong("id"), rs.getString("vendorName"), rs.getString("companyName"), rs.getString("gstNumber"), rs.getString("panNumber"), rs.getString("contactPerson"), rs.getString("mobile"), rs.getString("email"), rs.getString("address"), rs.getString("bankDetails"), rs.getString("category")));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<RegisteredUser> getRegisteredUsers() {
        List<RegisteredUser> list = new ArrayList<>();
        String sql = "SELECT * FROM registeredusers";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new RegisteredUser(rs.getString("name"), rs.getString("mobile"), rs.getString("email"), rs.getString("role"), rs.getString("password"), rs.getString("createdDate")));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<PurchaseOrder> getPurchaseOrders() {
        List<PurchaseOrder> list = new ArrayList<>();
        String sql = "SELECT * FROM purchaseorders";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new PurchaseOrder(rs.getLong("id"), rs.getString("poNumber"), rs.getString("date"), rs.getString("vendorName"), rs.getString("material"), rs.getString("quantity"), rs.getString("rate"), rs.getString("gst"), rs.getString("totalAmount")));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<VendorBill> getVendorBills() {
        List<VendorBill> list = new ArrayList<>();
        String sql = "SELECT * FROM vendorbills";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new VendorBill(rs.getLong("id"), rs.getString("vendorName"), rs.getString("invoiceNumber"), rs.getString("invoiceDate"), rs.getString("billAmount"), rs.getString("gst"), rs.getString("dueDate"), rs.getString("status")));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<VendorPayment> getVendorPayments() {
        List<VendorPayment> list = new ArrayList<>();
        String sql = "SELECT * FROM vendorpayments";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new VendorPayment(rs.getLong("id"), rs.getString("vendorName"), rs.getString("paymentDate"), rs.getString("paymentMode"), rs.getString("transactionNumber"), rs.getString("bank"), rs.getString("paidAmount"), rs.getString("balanceAmount"), rs.getString("status")));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<PettyCashEntry> getPettyCashEntries() {
        List<PettyCashEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM pettycashentries";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new PettyCashEntry(
                    rs.getLong("id"), rs.getString("date"), rs.getString("voucherNumber"), rs.getString("expenseCategory"), rs.getString("description"), rs.getString("employeeName"), rs.getString("amount"), rs.getString("paymentMode"), rs.getString("approvedBy")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

    public List<PettyCashApproval> getPettyCashApprovals() {
        List<PettyCashApproval> list = new ArrayList<>();
        String sql = "SELECT * FROM pettycashapprovals";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new PettyCashApproval(
                    rs.getLong("id"), rs.getString("requestedBy"), rs.getString("approvedBy"), rs.getString("amount"), rs.getString("remarks"), rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace(); throw mapSaveError(e);
        }
        return list;
    }

}
