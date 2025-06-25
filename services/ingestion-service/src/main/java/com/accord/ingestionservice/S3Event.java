package com.accord.ingestionservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// This file contains multiple records to match the nested JSON structure.
// Using 'records' is a modern, concise way to model immutable data carriers.

public record S3Event(
        @JsonProperty("Records") List<S3EventRecord> records
) {}

record S3EventRecord(
        S3Data s3
) {}

record S3Data(
        S3Bucket bucket,
        S3Object object
) {}

record S3Bucket(
        String name
) {}

record S3Object(
        // The 'key' is the full name/path of the file in the S3 bucket.
        String key
) {}