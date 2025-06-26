package com.accord.ingestionservice;

public record TransactionProcessedEvent(
        String bucketName,
        String fileKey,
        long fileSize,
        int pageCount,
        String textSnippet
) {}