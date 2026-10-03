package com.saivandan.crm.model;

public record PettyCashApproval(long id, String requestedBy, String approvedBy, String amount, String remarks, String status) {
    public PettyCashApproval(String requestedBy, String approvedBy, String amount, String remarks, String status) {
        this(0, requestedBy, approvedBy, amount, remarks, status);
    }
}
