package com.accord.fraud_detection_service;

// This must match the record in the other services
public record TransactionProcessedEvent(
        String bucketName,
        String fileKey,
        long fileSize,
        int pageCount,
        String textSnippet
) {}