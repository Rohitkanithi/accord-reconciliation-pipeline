package com.accord.ingestionservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);

    // Spring Boot automatically configures and provides an ObjectMapper bean for us to use.
    private final ObjectMapper objectMapper;

    // We use constructor injection to get the ObjectMapper bean. This is a best practice.
    public MessageListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SqsListener("${spring.cloud.aws.sqs.queue-name}")
    public void receiveMessage(Message<String> message) {
        String payload = message.getPayload();
        logger.info("====================================================");
        logger.info("SUCCESS: Received a new message from SQS!");
        logger.info("Raw Payload: {}", payload);

        try {
            // Use the ObjectMapper to parse the JSON string into our S3Event object
            S3Event s3Event = objectMapper.readValue(payload, S3Event.class);

            if (s3Event.records() != null && !s3Event.records().isEmpty()) {
                // S3 can sometimes send multiple records, but we'll process the first one.
                S3EventRecord record = s3Event.records().get(0);
                String bucketName = record.s3().bucket().name();
                String objectKey = record.s3().object().key();

                logger.info("-----> Extracted Bucket Name: {}", bucketName);
                logger.info("-----> Extracted File Key (Name): {}", objectKey);

                // OUR NEXT STEP WILL GO HERE:
                // Use the bucketName and objectKey to download the file from S3.
            }
        } catch (Exception e) {
            logger.error("Failed to parse S3 event JSON", e);
        }

        logger.info("====================================================");
    }
}