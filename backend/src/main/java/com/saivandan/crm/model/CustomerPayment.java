package com.saivandan.crm.model;

public record CustomerPayment(long id, String customer, String installmentType, String totalCost, String paidAmount, String pendingAmount, String dueDate, String history) {
    public CustomerPayment(String customer, String installmentType, String totalCost, String paidAmount, String pendingAmount, String dueDate, String history) {
        this(0, customer, installmentType, totalCost, paidAmount, pendingAmount, dueDate, history);
    }
}
