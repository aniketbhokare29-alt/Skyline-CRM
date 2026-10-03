package com.saivandan.crm.model;

public record Possession(long id, String customer, String unit, String finalInspection, String utilityConnection, String keyHandover, String possessionLetter, String status, String remarks) {
    public Possession(String customer, String unit, String finalInspection, String utilityConnection, String keyHandover, String possessionLetter, String status, String remarks) {
        this(0, customer, unit, finalInspection, utilityConnection, keyHandover, possessionLetter, status, remarks);
    }
}
