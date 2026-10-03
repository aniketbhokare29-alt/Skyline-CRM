package com.saivandan.crm.model;

public record Attendance(long id, String employeeId, String employeeName, String date, String status, String overtimeHours, String remarks) {
    public Attendance(String employeeId, String employeeName, String date, String status, String overtimeHours, String remarks) {
        this(0, employeeId, employeeName, date, status, overtimeHours, remarks);
    }
}
