package com.saivandan.crm.model;

public record VendorPayment(long id, String vendorName, String paymentDate, String paymentMode, String transactionNumber, String bank, String paidAmount, String balanceAmount, String status) {
    public VendorPayment(String vendorName, String paymentDate, String paymentMode, String transactionNumber, String bank, String paidAmount, String balanceAmount, String status) {
        this(0, vendorName, paymentDate, paymentMode, transactionNumber, bank, paidAmount, balanceAmount, status);
    }
}
