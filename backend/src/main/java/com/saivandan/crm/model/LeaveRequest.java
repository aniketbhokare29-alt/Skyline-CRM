package com.saivandan.crm.model;

public record LeaveRequest(long id, String employeeId, String employeeName, String fromDate, String toDate, String leaveType, String status, String reason) {
    public LeaveRequest(String employeeId, String employeeName, String fromDate, String toDate, String leaveType, String status, String reason) {
        this(0, employeeId, employeeName, fromDate, toDate, leaveType, status, reason);
    }
}
