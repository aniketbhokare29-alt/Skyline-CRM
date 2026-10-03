package com.saivandan.crm.model;

public record SiteVisit(long id, String customer, String visitDate, String visitTime, String pickupRequired, String executive, String status, String feedback) {
    public SiteVisit(String customer, String visitDate, String visitTime, String pickupRequired, String executive, String status, String feedback) {
        this(0, customer, visitDate, visitTime, pickupRequired, executive, status, feedback);
    }
}
