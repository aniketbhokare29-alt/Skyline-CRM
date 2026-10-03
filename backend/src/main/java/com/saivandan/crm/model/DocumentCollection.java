package com.saivandan.crm.model;

public record DocumentCollection(long id, String customer, String panCard, String aadhaarCard, String passportPhoto, String addressProof, String incomeProof, String bankStatement) {
    public DocumentCollection(String customer, String panCard, String aadhaarCard, String passportPhoto, String addressProof, String incomeProof, String bankStatement) {
        this(0, customer, panCard, aadhaarCard, passportPhoto, addressProof, incomeProof, bankStatement);
    }
}
