package com.saivandan.crm;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.saivandan.crm.Utils.readForm;
import static com.saivandan.crm.Utils.send;

/**
 * JSON API for the CRM.
 *
 * Replaces the HTML-rendering router that used to live in {@code App}. The
 * business rules, database access and report calculations are unchanged; the
 * only difference is that every endpoint answers with JSON instead of an HTML
 * page.
 */
public class Api {

    static final String SESSION_HEADER = "X-Session-Token";
    private static final Map<String, String> SESSIONS = new ConcurrentHashMap<>();
    // -----------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------

    /** Handles every /api request. Returns true when the path was an API path. */
    public static boolean handle(HttpExchange exchange, String method, String path) throws IOException {
        if (!path.equals("/api") && !path.startsWith("/api/")) {
            return false;
        }
        try {
            route(exchange, method, path);
        } catch (DuplicateException dup) {
            sendJson(exchange, 409, Map.of("error", dup.getMessage()));
        } catch (IllegalArgumentException badRequest) {
            sendJson(exchange, 400, Map.of("error", badRequest.getMessage()));
        } catch (Exception ex) {
            System.err.println("API request failed: " + exchange.getRequestMethod() + " "
                    + exchange.getRequestURI());
            ex.printStackTrace(System.err);
            sendJson(exchange, 500, Map.of("error", "Server error. Check api.err.log for details."));
        }
        return true;
    }

    private static void route(HttpExchange exchange, String method, String path) throws IOException {
        String resource = path.equals("/api") ? "" : path.substring("/api/".length());
        String policyResource = resource.contains("/") ? resource.substring(0, resource.indexOf('/')) : resource;

        // ---- public endpoints -------------------------------------
        if (method.equals("POST") && resource.equals("register")) {
            String creatorRole = authenticatedRole(exchange);
            if (!RolePolicy.canCreateUsers(creatorRole)) {
                sendJson(exchange, creatorRole == null ? 401 : 403,
                        Map.of("error", creatorRole == null ? "Admin Owner sign-in is required." : "Only ADMIN_OWNER can create users."));
                return;
            }
            register(exchange);
            return;
        }

        if (method.equals("POST") && resource.equals("login")) {
            login(exchange);
            return;
        }

        if (method.equals("GET") && resource.equals("health")) {
            sendJson(exchange, 200, Map.of("ok", true, "service", "skyline-crm-api", "version", "5", "db", App.STORE.dbPath()));
            return;
        }

        // ---- authenticated endpoints ------------------------------
        String role = authenticatedRole(exchange);
        if (role == null) {
            sendJson(exchange, 401, Map.of("error", "Not signed in"));
            return;
        }

        if (method.equals("POST") && resource.equals("logout")) {
            sessionToken(exchange).ifPresent(App.STORE::deleteSession);
            sendJson(exchange, 200, Map.of("ok", true));
            return;
        }

        if (method.equals("GET") && resource.equals("session")) {
            sendJson(exchange, 200, Map.of("role", role));
            return;
        }

        if (!RolePolicy.canAccess(role, policyResource)) {
            sendJson(exchange, 403, Map.of("error", "Your role does not have access to this module."));
            return;
        }

        if (method.equals("GET") && resource.equals("nav")) {
            sendJson(exchange, 200, Reports.navSections(role));
            return;
        }

        if (method.equals("GET") && resource.equals("dashboard")) {
            sendJson(exchange, 200, Reports.dashboard(role));
            return;
        }

        if (method.equals("GET") && resource.equals("finance-dashboard")) {
            sendJson(exchange, 200, Reports.financeDashboard(role));
            return;
        }

        if ((method.equals("PUT") || method.equals("DELETE")) && updateOrDeleteEndpoint(exchange, method, resource)) {
            return;
        }

        // ---- salary slip endpoint ------------------------------------
        if (method.equals("GET") && resource.startsWith("salary/") && resource.endsWith("/slip")) {
            String idStr = resource.substring("salary/".length(), resource.length() - "/slip".length());
            try {
                long id = Long.parseLong(idStr);
                generateSalarySlip(exchange, id);
            } catch (NumberFormatException ex) {
                sendJson(exchange, 400, Map.of("error", "Invalid salary ID"));
            } catch (SQLException ex) {
                sendJson(exchange, 500, Map.of("error", "Database error: " + ex.getMessage()));
            }
            return;
        }

        if (!method.equals("GET") && !method.equals("POST")) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        // ---- module list endpoints ---------------------------------
        if (method.equals("GET")) {
            if (listEndpoint(exchange, resource)) {
                return;
            }
        }

        // ---- module save endpoints ---------------------------------
        if (method.equals("POST") && saveEndpoint(exchange, resource)) {
            return;
        }

        sendJson(exchange, 404, Map.of("error", "Page not found"));
    }

    // -----------------------------------------------------------------
    // Auth
    // -----------------------------------------------------------------

    private static void login(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        String selectedRoleValue = form.getOrDefault("role", RolePolicy.ADMIN_OWNER);
        if (!RolePolicy.isRecognized(selectedRoleValue)) {
            sendJson(exchange, 400, Map.of("error", "Invalid role."));
            return;
        }
        String selectedRole = RolePolicy.normalize(selectedRoleValue);
        String email = form.getOrDefault("email", "");
        String password = form.getOrDefault("password", "");

        String loginRole = email.isBlank() ? null
                : App.STORE.authenticateRegisteredUser(email, password, selectedRole);

        if (loginRole == null) {
            sendJson(exchange, 401, Map.of("error", "Invalid password for " + selectedRole + "."));
            return;
        }

        String token = UUID.randomUUID().toString();
        App.STORE.saveSession(token, loginRole);
        sendJson(exchange, 200, Map.of("token", token, "role", loginRole));
    }

    private static void register(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        String name = form.getOrDefault("name", "").trim();
        String email = form.getOrDefault("email", "").trim();
        String password = form.getOrDefault("password", "");
        String confirmPassword = form.getOrDefault("confirmPassword", "");
        String requestedRoleValue = form.getOrDefault("role", RolePolicy.EMPLOYEE);
        if (!RolePolicy.isRecognized(requestedRoleValue)) {
            sendJson(exchange, 400, Map.of("error", "Invalid role."));
            return;
        }
        String requestedRole = RolePolicy.normalize(requestedRoleValue);
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            sendJson(exchange, 400, Map.of("error", "Name, email and password are required."));
            return;
        }
        if (!email.contains("@")) {
            sendJson(exchange, 400, Map.of("error", "Enter a valid email address."));
            return;
        }
        if (!password.equals(confirmPassword)) {
            sendJson(exchange, 400, Map.of("error", "Password and confirm password must match."));
            return;
        }
        if (!RolePolicy.EMPLOYEE.equals(requestedRole) && !RolePolicy.AGENT.equals(requestedRole)) {
            sendJson(exchange, 400, Map.of("error", "New users must be EMPLOYEE or AGENT."));
            return;
        }
        form.put("role", requestedRole);
        App.STORE.addRegisteredUser(form);
        sendJson(exchange, 200, Map.of(
                "ok", true,
                "message", "Account created. Sign in with your email and password."
        ));
    }

    static String authenticatedRole(HttpExchange exchange) {
        return sessionToken(exchange).flatMap(App.STORE::findSessionRole).orElse(null);
    }

    static Optional<String> sessionToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
        if (header != null && !header.isBlank()) {
            return Optional.of(header.trim());
        }
        return exchange.getRequestHeaders().getOrDefault("Cookie", List.of()).stream()
                .flatMap(cookieHeader -> Arrays.stream(cookieHeader.split(";")))
                .map(String::trim)
                .filter(cookie -> cookie.startsWith("crm_session="))
                .map(cookie -> cookie.substring("crm_session=".length()))
                .filter(token -> !token.isBlank())
                .findFirst();
    }

    // -----------------------------------------------------------------
    // GET: record lists
    // -----------------------------------------------------------------

    private static boolean listEndpoint(HttpExchange exchange, String resource) throws IOException {
        switch (resource) {
            case "enquiries" -> items(exchange, App.STORE.getEnquiries());
            case "properties" -> items(exchange, App.STORE.getProperties());
            case "followups" -> items(exchange, App.STORE.getFollowUps());
            case "qualification" -> items(exchange, App.STORE.getQualifications());
            case "site-visits" -> items(exchange, App.STORE.getSiteVisits());
            case "negotiations" -> items(exchange, App.STORE.getNegotiations());
            case "bookings" -> items(exchange, App.STORE.getBookings());
            case "documents" -> items(exchange, App.STORE.getDocumentCollections());
            case "loans" -> items(exchange, App.STORE.getLoans());
            case "agreements" -> items(exchange, App.STORE.getAgreements());
            case "possession" -> items(exchange, App.STORE.getPossessions());
            case "support" -> items(exchange, App.STORE.getSupportTickets());
            case "employees" -> items(exchange, App.STORE.getEmployees());
            case "attendance" -> items(exchange, App.STORE.getAttendance());
            case "leaves" -> items(exchange, App.STORE.getLeaveRequests());
            case "vendors" -> items(exchange, App.STORE.getVendors());
            case "purchase-orders" -> items(exchange, App.STORE.getPurchaseOrders());
            case "petty-cash-approvals" -> items(exchange, App.STORE.getPettyCashApprovals());
            case "payments" -> {
                Map<String, Object> body = items(App.STORE.getCustomerPayments());
                body.put("totalPaid", Reports.paymentsTotalPaid());
                body.put("totalPending", Reports.paymentsTotalPending());
                sendJson(exchange, 200, body);
            }
            case "salary" -> {
                Map<String, Object> body = items(App.STORE.getSalaryPayments());
                body.put("totalGross", Reports.salaryTotalGross());
                body.put("totalNet", Reports.salaryTotalNet());
                sendJson(exchange, 200, body);
            }
            case "vendor-bills" -> {
                Map<String, Object> body = items(App.STORE.getVendorBills());
                body.put("billCount", App.STORE.getVendorBills().size());
                body.put("outstanding", Reports.vendorBillsOutstanding());
                sendJson(exchange, 200, body);
            }
            case "vendor-payments" -> {
                Map<String, Object> body = items(App.STORE.getVendorPayments());
                body.put("totalPaid", Reports.vendorPaymentsTotalPaid());
                body.put("totalBalance", Reports.vendorPaymentsTotalBalance());
                sendJson(exchange, 200, body);
            }
            case "petty-cash" -> {
                Map<String, Object> body = items(App.STORE.getPettyCashEntries());
                body.put("entryCount", App.STORE.getPettyCashEntries().size());
                body.put("cashSpent", Reports.pettyCashSpent());
                sendJson(exchange, 200, body);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> items(List<?> records) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", records);
        return body;
    }

    private static void items(HttpExchange exchange, List<?> records) throws IOException {
        sendJson(exchange, 200, items(records));
    }

    // -----------------------------------------------------------------
    // POST: record creation
    // -----------------------------------------------------------------

    private static final Map<String, List<String>> REQUIRED_FIELDS = buildRequiredFields();

    private static Map<String, List<String>> buildRequiredFields() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("enquiries", List.of("name", "mobile"));
        m.put("properties", List.of("unit", "wing", "floor"));
        m.put("followups", List.of("customer"));
        m.put("qualification", List.of("customer"));
        m.put("site-visits", List.of("customer"));
        m.put("negotiations", List.of("customer", "unit"));
        m.put("bookings", List.of("customer", "bookingAmount", "unit"));
        m.put("documents", List.of("customer"));
        m.put("loans", List.of("customer"));
        m.put("agreements", List.of("customer", "unit"));
        m.put("payments", List.of("customer"));
        m.put("possession", List.of("customer", "unit"));
        m.put("support", List.of("customer", "unit"));
        m.put("employees", List.of("employeeId", "name"));
        m.put("attendance", List.of("employeeId", "employeeName", "date"));
        m.put("leaves", List.of("employeeId", "employeeName", "fromDate", "toDate"));
        m.put("salary", List.of("employeeId", "employeeName", "salaryMonth"));
        m.put("vendors", List.of("vendorName"));
        m.put("purchase-orders", List.of("poNumber", "vendorName"));
        m.put("vendor-bills", List.of("vendorName", "invoiceNumber"));
        m.put("vendor-payments", List.of("vendorName"));
        m.put("petty-cash", List.of("voucherNumber"));
        m.put("petty-cash-approvals", List.of("requestedBy"));
        return m;
    }

    private static boolean saveEndpoint(HttpExchange exchange, String resource) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        boolean isMultipart = contentType != null && contentType.startsWith("multipart/form-data");
        Map<String, String> form;
        
        if (isMultipart && resource.equals("documents")) {
            form = parseMultipartWithFiles(exchange);
        } else {
            form = readForm(exchange);
        }
        
        // Validate required fields
        List<String> required = REQUIRED_FIELDS.get(resource);
        if (required != null) {
            for (String field : required) {
                String value = form.getOrDefault(field, "").trim();
                if (value.isBlank()) {
                    sendJson(exchange, 400, Map.of("error", "Missing required field: " + field));
                    return true;
                }
            }
        }
        
        switch (resource) {
            case "enquiries" -> App.STORE.addEnquiry(form);
            case "properties" -> App.STORE.addProperty(form);
            case "followups" -> App.STORE.addFollowUp(form);
            case "qualification" -> App.STORE.addQualification(form);
            case "site-visits" -> App.STORE.addSiteVisit(form);
            case "negotiations" -> App.STORE.addNegotiation(form);
            case "bookings" -> App.STORE.addBooking(form);
            case "documents" -> App.STORE.addDocumentCollection(form);
            case "loans" -> App.STORE.addLoan(form);
            case "agreements" -> App.STORE.addAgreement(form);
            case "payments" -> App.STORE.addCustomerPayment(form);
            case "possession" -> App.STORE.addPossession(form);
            case "support" -> App.STORE.addSupportTicket(form);
            case "employees" -> App.STORE.addEmployee(form);
            case "attendance" -> App.STORE.addAttendance(form);
            case "leaves" -> App.STORE.addLeaveRequest(form);
            case "salary" -> App.STORE.addSalaryPayment(form);
            case "vendors" -> App.STORE.addVendor(form);
            case "purchase-orders" -> App.STORE.addPurchaseOrder(form);
            case "vendor-bills" -> App.STORE.addVendorBill(form);
            case "vendor-payments" -> App.STORE.addVendorPayment(form);
            case "petty-cash" -> App.STORE.addPettyCashEntry(form);
            case "petty-cash-approvals" -> App.STORE.addPettyCashApproval(form);
            default -> {
                return false;
            }
        }
        sendJson(exchange, 201, Map.of("ok", true));
        return true;
    }

    private static boolean updateOrDeleteEndpoint(HttpExchange exchange, String method, String resource) throws IOException {
        String[] parts = resource.split("/", -1);
        if (parts.length != 2 || (!parts[0].equals("bookings") && !parts[0].equals("payments") && !parts[0].equals("enquiries") && !parts[0].equals("qualification") && !parts[0].equals("followups") && !parts[0].equals("site-visits") && !parts[0].equals("properties") && !parts[0].equals("negotiations") && !parts[0].equals("loans") && !parts[0].equals("agreements") && !parts[0].equals("possession") && !parts[0].equals("support") && !parts[0].equals("vendors") && !parts[0].equals("purchase-orders") && !parts[0].equals("vendor-bills") && !parts[0].equals("vendor-payments") && !parts[0].equals("documents") && !parts[0].equals("employees") && !parts[0].equals("attendance") && !parts[0].equals("leaves") && !parts[0].equals("salary") && !parts[0].equals("petty-cash") && !parts[0].equals("petty-cash-approvals"))) {
            return false;
        }
        long id;
        try {
            id = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            sendJson(exchange, 400, Map.of("error", "A valid record id is required."));
            return true;
        }
        if (method.equals("DELETE")) {
            switch (parts[0]) {
                case "bookings" -> App.STORE.deleteBooking(id);
                case "payments" -> App.STORE.deleteCustomerPayment(id);
                case "enquiries" -> App.STORE.deleteEnquiry(id);
                case "qualification" -> App.STORE.deleteQualification(id);
                case "followups" -> App.STORE.deleteFollowUp(id);
                case "site-visits" -> App.STORE.deleteSiteVisit(id);
                case "properties" -> App.STORE.deleteProperty(id);
                case "negotiations" -> App.STORE.deleteNegotiation(id);
                case "loans" -> App.STORE.deleteLoan(id);
                case "agreements" -> App.STORE.deleteAgreement(id);
                case "possession" -> App.STORE.deletePossession(id);
                case "support" -> App.STORE.deleteSupportTicket(id);
                case "vendors" -> App.STORE.deleteVendor(id);
                case "purchase-orders" -> App.STORE.deletePurchaseOrder(id);
                case "vendor-bills" -> App.STORE.deleteVendorBill(id);
                case "vendor-payments" -> App.STORE.deleteVendorPayment(id);
                case "documents" -> App.STORE.deleteDocumentCollection(id);
                case "employees" -> App.STORE.deleteEmployee(id);
                case "attendance" -> App.STORE.deleteAttendance(id);
                case "leaves" -> App.STORE.deleteLeaveRequest(id);
                case "salary" -> App.STORE.deleteSalaryPayment(id);
                case "petty-cash" -> App.STORE.deletePettyCashEntry(id);
                case "petty-cash-approvals" -> App.STORE.deletePettyCashApproval(id);
            }
            sendJson(exchange, 200, Map.of("ok", true));
            return true;
        }
        Map<String, String> form = readForm(exchange);
        switch (parts[0]) {
            case "bookings" -> App.STORE.updateBooking(id, form);
            case "payments" -> App.STORE.updateCustomerPayment(id, form);
            case "enquiries" -> App.STORE.updateEnquiry(id, form);
            case "qualification" -> App.STORE.updateQualification(id, form);
            case "followups" -> App.STORE.updateFollowUp(id, form);
            case "site-visits" -> App.STORE.updateSiteVisit(id, form);
            case "properties" -> App.STORE.updateProperty(id, form);
            case "negotiations" -> App.STORE.updateNegotiation(id, form);
            case "loans" -> App.STORE.updateLoan(id, form);
            case "agreements" -> App.STORE.updateAgreement(id, form);
            case "possession" -> App.STORE.updatePossession(id, form);
            case "support" -> App.STORE.updateSupportTicket(id, form);
            case "vendors" -> App.STORE.updateVendor(id, form);
            case "purchase-orders" -> App.STORE.updatePurchaseOrder(id, form);
            case "vendor-bills" -> App.STORE.updateVendorBill(id, form);
            case "vendor-payments" -> App.STORE.updateVendorPayment(id, form);
            case "documents" -> App.STORE.updateDocumentCollection(id, form);
            case "employees" -> App.STORE.updateEmployee(id, form);
            case "attendance" -> App.STORE.updateAttendance(id, form);
            case "leaves" -> App.STORE.updateLeaveRequest(id, form);
            case "salary" -> App.STORE.updateSalaryPayment(id, form);
            case "petty-cash" -> App.STORE.updatePettyCashEntry(id, form);
            case "petty-cash-approvals" -> App.STORE.updatePettyCashApproval(id, form);
        }
        sendJson(exchange, 200, Map.of("ok", true));
        return true;
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", Json.write(body));
    }

    private static Map<String, String> parseMultipartWithFiles(HttpExchange exchange) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String boundary = getBoundary(contentType);
        if (boundary == null) {
            return values;
        }
        
        byte[] data = exchange.getRequestBody().readAllBytes();
        String body = new String(data, StandardCharsets.ISO_8859_1);
        String delimiter = "--" + boundary;
        
        String[] parts = body.split(Pattern.quote(delimiter));
        for (String part : parts) {
            if (part.trim().isEmpty() || part.contains("--")) continue;
            
            int headerEnd = part.indexOf("\r\n\r\n");
            if (headerEnd == -1) continue;
            
            String headers = part.substring(0, headerEnd);
            String content = part.substring(headerEnd + 4);
            if (content.endsWith("\r\n")) {
                content = content.substring(0, content.length() - 2);
            }
            
            String name = extractName(headers);
            String filename = extractFilename(headers);
            if (name != null) {
                if (filename != null && !filename.isEmpty()) {
                    // Save file
                    String savedPath = saveFile(name, filename, content.getBytes(StandardCharsets.ISO_8859_1));
                    values.put(name, savedPath);
                } else {
                    values.put(name, content);
                }
            }
        }
        return values;
    }

    private static String getBoundary(String contentType) {
        Pattern pattern = Pattern.compile("boundary=([^;]+)");
        Matcher matcher = pattern.matcher(contentType);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private static String extractName(String headers) {
        Pattern pattern = Pattern.compile("name=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(headers);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractFilename(String headers) {
        Pattern pattern = Pattern.compile("filename=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(headers);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String saveFile(String fieldName, String originalFilename, byte[] content) throws IOException {
        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }
        String savedName = fieldName + "_" + System.currentTimeMillis() + extension;
        Path filePath = uploadDir.resolve(savedName);
        Files.write(filePath, content);
        return "/uploads/" + savedName;
    }

    private static void generateSalarySlip(HttpExchange exchange, long id) throws IOException, SQLException {
        try (Connection conn = App.STORE.openConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM salarypayments WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    sendJson(exchange, 404, Map.of("error", "Salary payment not found"));
                    return;
                }
                
                String employeeName = rs.getString("employeeName");
                String salaryMonth = rs.getString("salaryMonth");
                String basicSalary = rs.getString("basicSalary");
                String hra = rs.getString("hra");
                String incentives = rs.getString("incentives");
                String bonus = rs.getString("bonus");
                String deductions = rs.getString("deductions");
                String grossSalary = rs.getString("grossSalary");
                String netSalary = rs.getString("netSalary");
                String paymentDate = rs.getString("paymentDate");
                String paymentMode = rs.getString("paymentMode");
                String paymentStatus = rs.getString("paymentStatus");
                
                long gross = Long.parseLong(grossSalary == null || grossSalary.isBlank() ? "0" : grossSalary);
                long net = Long.parseLong(netSalary == null || netSalary.isBlank() ? "0" : netSalary);
                
                String html = "<!doctype html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "  <meta charset=\"utf-8\">\n" +
                    "  <title>Salary Slip - " + Utils.escape(employeeName) + "</title>\n" +
                    "  <style>\n" +
                    "    body { font-family: Arial, sans-serif; margin: 40px; }\n" +
                    "    .header { text-align: center; margin-bottom: 30px; }\n" +
                    "    .header h1 { margin: 0; color: #0f766e; }\n" +
                    "    .header p { margin: 5px 0; color: #666; }\n" +
                    "    table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }\n" +
                    "    th, td { padding: 10px; border: 1px solid #ddd; text-align: left; }\n" +
                    "    th { background: #0f766e; color: white; }\n" +
                    "    .totals { font-weight: bold; background: #f0fdfa; }\n" +
                    "    .footer { margin-top: 40px; display: flex; justify-content: space-between; }\n" +
                    "    .sign { text-align: center; width: 200px; }\n" +
                    "    .sign-line { border-top: 1px solid #333; margin-top: 50px; padding-top: 5px; }\n" +
                    "    @media print { body { margin: 0; } .no-print { display: none; } }\n" +
                    "  </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "  <div class=\"header\">\n" +
                    "    <h1>Skyline CRM - Salary Slip</h1>\n" +
                    "    <p>" + Utils.escape(salaryMonth) + "</p>\n" +
                    "  </div>\n" +
                    "  <table>\n" +
                    "    <tr><th>Employee Name</th><td>" + Utils.escape(employeeName) + "</td></tr>\n" +
                    "    <tr><th>Salary Month</th><td>" + Utils.escape(salaryMonth) + "</td></tr>\n" +
                    "    <tr><th>Payment Date</th><td>" + Utils.escape(paymentDate) + "</td></tr>\n" +
                    "    <tr><th>Payment Mode</th><td>" + Utils.escape(paymentMode) + "</td></tr>\n" +
                    "    <tr><th>Status</th><td>" + Utils.escape(paymentStatus) + "</td></tr>\n" +
                    "  </table>\n" +
                    "  <table>\n" +
                    "    <thead><tr><th>Component</th><th>Amount (₹)</th></tr></thead>\n" +
                    "    <tbody>\n" +
                    "      <tr><td>Basic Salary</td><td>" + Utils.escape(basicSalary) + "</td></tr>\n" +
                    "      <tr><td>HRA</td><td>" + Utils.escape(hra) + "</td></tr>\n" +
                    "      <tr><td>Incentives</td><td>" + Utils.escape(incentives) + "</td></tr>\n" +
                    "      <tr><td>Bonus</td><td>" + Utils.escape(bonus) + "</td></tr>\n" +
                    "      <tr class=\"totals\"><td>Gross Salary</td><td>" + gross + "</td></tr>\n" +
                    "      <tr><td>Deductions</td><td>" + Utils.escape(deductions) + "</td></tr>\n" +
                    "      <tr class=\"totals\"><td>Net Salary</td><td>" + net + "</td></tr>\n" +
                    "    </tbody>\n" +
                    "  </table>\n" +
                    "  <div class=\"footer\">\n" +
                    "    <div class=\"sign\"><div class=\"sign-line\">Employee Signature</div></div>\n" +
                    "    <div class=\"sign\"><div class=\"sign-line\">Authorized Signatory</div></div>\n" +
                    "  </div>\n" +
                    "  <script>window.onload = function() { window.print(); }</script>\n" +
                    "</body>\n" +
                    "</html>";
                
                send(exchange, 200, "text/html; charset=utf-8", html);
            }
        }
    }
}
