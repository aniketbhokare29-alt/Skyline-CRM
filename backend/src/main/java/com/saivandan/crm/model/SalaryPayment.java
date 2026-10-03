package com.saivandan.crm.model;

public record SalaryPayment(long id, String employeeId, String employeeName, String salaryMonth, String basicSalary, String hra, String incentives, String bonus, String deductions, String grossSalary, String netSalary, String paymentDate, String paymentMode, String paymentStatus) {
    public SalaryPayment(String employeeId, String employeeName, String salaryMonth, String basicSalary, String hra, String incentives, String bonus, String deductions, String grossSalary, String netSalary, String paymentDate, String paymentMode, String paymentStatus) {
        this(0, employeeId, employeeName, salaryMonth, basicSalary, hra, incentives, bonus, deductions, grossSalary, netSalary, paymentDate, paymentMode, paymentStatus);
    }
}
