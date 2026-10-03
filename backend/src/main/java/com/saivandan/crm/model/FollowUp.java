package com.saivandan.crm.model;

public record FollowUp(long id, String customer, String type, String date, String time, String nextDate, String executive, String remarks) {
    public FollowUp(String customer, String type, String date, String time, String nextDate, String executive, String remarks) {
        this(0, customer, type, date, time, nextDate, executive, remarks);
    }
}
