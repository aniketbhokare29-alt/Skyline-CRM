package com.saivandan.crm.model;

public record LeadQualification(long id, String customer, String budget, String loanRequired, String preferredLocation, String flatType, String purchaseTimeline, String purpose, String status, String notes) {
    public LeadQualification(String customer, String budget, String loanRequired, String preferredLocation, String flatType, String purchaseTimeline, String purpose, String status, String notes) {
        this(0, customer, budget, loanRequired, preferredLocation, flatType, purchaseTimeline, purpose, status, notes);
    }
}
