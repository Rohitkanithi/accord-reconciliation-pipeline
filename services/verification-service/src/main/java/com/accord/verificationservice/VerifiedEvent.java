package com.accord.verificationservice;

import java.time.Instant;

public record VerifiedEvent(
        String status,
        String fileKey,
        String verificationId,
        Instant verificationTimestamp
) {}