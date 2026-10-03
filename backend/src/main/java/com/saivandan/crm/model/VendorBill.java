package com.saivandan.crm.model;

public record VendorBill(long id, String vendorName, String invoiceNumber, String invoiceDate, String billAmount, String gst, String dueDate, String status) {
    public VendorBill(String vendorName, String invoiceNumber, String invoiceDate, String billAmount, String gst, String dueDate, String status) {
        this(0, vendorName, invoiceNumber, invoiceDate, billAmount, gst, dueDate, status);
    }
}
