package com.saivandan.crm.model;

public record PurchaseOrder(long id, String poNumber, String date, String vendorName, String material, String quantity, String rate, String gst, String totalAmount) {
    public PurchaseOrder(String poNumber, String date, String vendorName, String material, String quantity, String rate, String gst, String totalAmount) {
        this(0, poNumber, date, vendorName, material, quantity, rate, gst, totalAmount);
    }
}
