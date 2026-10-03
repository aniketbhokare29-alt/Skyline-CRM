package com.saivandan.crm.model;

public record Agreement(long id, String customer, String unit, String agreementDate, String stampDuty, String registrationDate, String registrationNumber) {
    public Agreement(String customer, String unit, String agreementDate, String stampDuty, String registrationDate, String registrationNumber) {
        this(0, customer, unit, agreementDate, stampDuty, registrationDate, registrationNumber);
    }
}
