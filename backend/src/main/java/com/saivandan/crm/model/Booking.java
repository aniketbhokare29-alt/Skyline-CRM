package com.saivandan.crm.model;

public record Booking(long id, String customer, String bookingAmount, String bookingDate, String unit, String floor, String flatType, String status) {
    public Booking(String customer, String bookingAmount, String bookingDate, String unit, String floor, String flatType, String status) {
        this(0, customer, bookingAmount, bookingDate, unit, floor, flatType, status);
    }
}
