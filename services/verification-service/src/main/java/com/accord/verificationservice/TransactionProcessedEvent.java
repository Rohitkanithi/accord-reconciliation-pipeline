package com.accord.verificationservice;

// object structure must match the event sent by the IngestionService
public record TransactionProcessedEvent(
        String bucketName,
        String fileKey,
        long fileSize,
        int pageCount,
        String textSnippet
) {}