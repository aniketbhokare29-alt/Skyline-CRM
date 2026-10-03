package com.saivandan.crm.model;

public record Vendor(long id, String vendorName, String companyName, String gstNumber, String panNumber, String contactPerson, String mobile, String email, String address, String bankDetails, String category) {
    public Vendor(String vendorName, String companyName, String gstNumber, String panNumber, String contactPerson, String mobile, String email, String address, String bankDetails, String category) {
        this(0, vendorName, companyName, gstNumber, panNumber, contactPerson, mobile, email, address, bankDetails, category);
    }
}
