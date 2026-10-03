package com.saivandan.crm.model;

public record Enquiry(long id, String name, String mobile, String email, String city, String budget, String interestedProperty, String source, String status) {
    public Enquiry(String name, String mobile, String email, String city, String budget, String interestedProperty, String source, String status) {
        this(0, name, mobile, email, city, budget, interestedProperty, source, status);
    }
}
