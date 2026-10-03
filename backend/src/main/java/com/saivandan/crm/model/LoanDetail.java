package com.saivandan.crm.model;

public record LoanDetail(long id, String customer, String bankName, String loanAmount, String emi, String sanctionDate, String status) {
    public LoanDetail(String customer, String bankName, String loanAmount, String emi, String sanctionDate, String status) {
        this(0, customer, bankName, loanAmount, emi, sanctionDate, status);
    }
}
