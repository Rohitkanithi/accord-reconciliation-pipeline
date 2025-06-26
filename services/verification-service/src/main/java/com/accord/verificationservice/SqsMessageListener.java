package com.accord.verificationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SqsMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(SqsMessageListener.class);
    private final ObjectMapper objectMapper;

    public SqsMessageListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Listen for a raw String message
    @SqsListener("${spring.cloud.aws.sqs.queue-name}")
    public void receiveMessage(String messageJson) {
        logger.info("====================================================");
        logger.info("VERIFICATION SERVICE Received new message!");
        try {
            // 1. Parse the outer SNS/SQS message structure
            JsonNode rootNode = objectMapper.readTree(messageJson);
            // 2. Extract the actual 'Message' payload, which is our event JSON
            String eventPayload = rootNode.get("Message").asText();
            // 3. Parse the inner JSON into our event object
            TransactionProcessedEvent event = objectMapper.readValue(eventPayload, TransactionProcessedEvent.class);

            logger.info("File to verify: {}/{}", event.bucketName(), event.fileKey());
            logger.info("Page count: {}", event.pageCount());
            logger.info("-----> SUCCESS: Event parsed correctly!");
            logger.info("====================================================");

        } catch (Exception e) {
            logger.error("Error parsing SNS message JSON", e);
        }
    }
}