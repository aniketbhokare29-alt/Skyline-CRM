package com.saivandan.crm.model;

public record Negotiation(long id, String customer, String unit, String offeredPrice, String discount, String managementApproval, String specialOffer) {
    public Negotiation(String customer, String unit, String offeredPrice, String discount, String managementApproval, String specialOffer) {
        this(0, customer, unit, offeredPrice, discount, managementApproval, specialOffer);
    }
}
