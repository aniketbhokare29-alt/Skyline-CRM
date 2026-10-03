package com.saivandan.crm.model;

public record PettyCashEntry(long id, String date, String voucherNumber, String expenseCategory, String description, String employeeName, String amount, String paymentMode, String approvedBy) {
    public PettyCashEntry(String date, String voucherNumber, String expenseCategory, String description, String employeeName, String amount, String paymentMode, String approvedBy) {
        this(0, date, voucherNumber, expenseCategory, description, employeeName, amount, paymentMode, approvedBy);
    }
}
