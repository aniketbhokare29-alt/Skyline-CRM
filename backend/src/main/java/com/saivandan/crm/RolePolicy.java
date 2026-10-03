package com.saivandan.crm;

import java.util.Locale;
import java.util.Set;

/** Central role names and server-side permission checks. */
public final class RolePolicy {
    public static final String ADMIN_OWNER = "ADMIN_OWNER";
    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String AGENT = "AGENT";
    public static final Set<String> VALID_ROLES = Set.of(ADMIN_OWNER, EMPLOYEE, AGENT);

    private RolePolicy() {
    }

    /** Converts the five legacy login roles into the required role model. */
    public static String normalize(String role) {
        if (role == null) {
            return EMPLOYEE;
        }
        String value = role.trim();
        if (VALID_ROLES.contains(value)) {
            return value;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "admin", "admin owner", "admin_owner" -> ADMIN_OWNER;
            case "agent" -> AGENT;
            default -> EMPLOYEE;
        };
    }

    public static boolean isValid(String role) {
        return VALID_ROLES.contains(role);
    }

    public static boolean isRecognized(String role) {
        if (role == null) {
            return false;
        }
        return switch (role.trim().toLowerCase(Locale.ROOT)) {
            case "admin_owner", "admin owner", "admin", "employee", "agent",
                    "sales executive", "accountant", "hr/payroll", "manager" -> true;
            default -> false;
        };
    }

    public static boolean canCreateUsers(String role) {
        return ADMIN_OWNER.equals(role);
    }

    public static boolean canAccess(String role, String resource) {
        if (ADMIN_OWNER.equals(role)) {
            return true;
        }
        if ("dashboard".equals(resource) || "nav".equals(resource) || "session".equals(resource)
                || "logout".equals(resource)) {
            return true;
        }
        if (EMPLOYEE.equals(role)) {
            return Set.of("enquiries", "followups", "qualification", "bookings", "payments",
                    "vendor-payments", "petty-cash").contains(resource);
        }
        if (AGENT.equals(role)) {
            return Set.of("enquiries", "followups", "qualification", "bookings", "payments")
                    .contains(resource);
        }
        return false;
    }
}
