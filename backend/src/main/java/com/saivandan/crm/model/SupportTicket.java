package com.saivandan.crm.model;

public record SupportTicket(long id, String customer, String unit, String category, String priority, String status, String assignedTo, String description) {
    public SupportTicket(String customer, String unit, String category, String priority, String status, String assignedTo, String description) {
        this(0, customer, unit, category, priority, status, assignedTo, description);
    }
}
