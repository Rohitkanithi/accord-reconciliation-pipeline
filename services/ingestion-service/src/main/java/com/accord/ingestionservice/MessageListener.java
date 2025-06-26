package com.accord.ingestionservice; // Or your package name

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.net.URLDecoder; // <-- NEW IMPORT
import java.nio.charset.StandardCharsets; // <-- NEW IMPORT

@Component
public class MessageListener { // Or SqsListener

    private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);

    private final ObjectMapper objectMapper;
    private final S3Client s3Client;

    public MessageListener(ObjectMapper objectMapper, S3Client s3Client) {
        this.objectMapper = objectMapper;
        this.s3Client = s3Client;
    }

    @SqsListener("${spring.cloud.aws.sqs.queue-name}")
    public void receiveMessage(Message<String> message) {
        logger.info("====================================================");
        logger.info("Received S3 event notification via SQS!");
        logger.info("Raw Payload: {}", message.getPayload());

        try {
            S3Event s3Event = objectMapper.readValue(message.getPayload(), S3Event.class);

            if (s3Event.records() != null && !s3Event.records().isEmpty()) {
                S3EventRecord record = s3Event.records().get(0);
                String bucketName = record.s3().bucket().name();

                // --- THE FIX IS HERE ---
                // 1. Get the URL-encoded key from the event.
                String encodedObjectKey = record.s3().object().key();
                // 2. Decode the key to handle special characters like spaces ('+').
                String objectKey = URLDecoder.decode(encodedObjectKey, StandardCharsets.UTF_8);
                // --- END OF FIX ---

                logger.info("-----> Extracted Bucket: {}", bucketName);
                logger.info("-----> Extracted and DECODED File Key: {}", objectKey); // Use the new decoded key

                logger.info("-----> Attempting to download file from S3...");
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey) // Use the decoded key
                        .build();

                ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
                long fileSize = s3Object.response().contentLength();

                logger.info("-----> SUCCESS: File downloaded successfully! Size: {} bytes", fileSize);

                s3Object.close();
            }
        } catch (Exception e) {
            logger.error("Failed to process S3 event or download file", e);
        }
        logger.info("====================================================");
    }
}