package com.saivandan.crm.model;

public record DocumentRecord(
        long id,
        String customerId,
        String customer,
        String bookingId,
        String documentType,
        String originalFileName,
        String storedFileName,
        String filePath,
        String fileType,
        long fileSize,
        String verificationStatus,
        String uploadedBy,
        String uploadedDate,
        int version
) {}
