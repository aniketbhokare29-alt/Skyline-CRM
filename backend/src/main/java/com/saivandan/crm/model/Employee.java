package com.saivandan.crm.model;

public record Employee(long id, String employeeId, String name, String department, String designation, String mobile, String email, String dateOfJoining, String salaryStructure, String bankDetails, String identityDetails, String statutoryDetails) {
    public Employee(String employeeId, String name, String department, String designation, String mobile, String email, String dateOfJoining, String salaryStructure, String bankDetails, String identityDetails, String statutoryDetails) {
        this(0, employeeId, name, department, designation, mobile, email, dateOfJoining, salaryStructure, bankDetails, identityDetails, statutoryDetails);
    }
}
