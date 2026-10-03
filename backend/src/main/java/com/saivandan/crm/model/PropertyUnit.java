package com.saivandan.crm.model;

public record PropertyUnit(long id, String unit, String wing, String floor, String flatType, String area, String price, String parking, String availability, String amenities) {
    public PropertyUnit(String unit, String wing, String floor, String flatType, String area, String price, String parking, String availability, String amenities) {
        this(0, unit, wing, floor, flatType, area, price, parking, availability, amenities);
    }
}
