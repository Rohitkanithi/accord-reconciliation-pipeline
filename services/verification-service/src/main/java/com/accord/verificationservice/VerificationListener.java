package com.accord.verificationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VerificationListener {

    private static final Logger logger = LoggerFactory.getLogger(VerificationListener.class);
    private final ObjectMapper objectMapper;

    public VerificationListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // We listen for the raw JSON String from the SQS queue
    @SqsListener("${spring.cloud.aws.sqs.queue-name}")
    public void receiveMessage(String snsMessageJson) {
        logger.info("====================================================");
        logger.info("VERIFICATION SERVICE: Received new message from queue!");
        try {
            // Step 1: Parse the outer SNS envelope to get to the 'Message' field
            JsonNode rootNode = objectMapper.readTree(snsMessageJson);
            String eventPayload = rootNode.get("Message").asText();

            // Step 2: Parse the actual message payload into our event object
            TransactionProcessedEvent event = objectMapper.readValue(eventPayload, TransactionProcessedEvent.class);

            logger.info("-----> Event parsed successfully for file: {}", event.fileKey());
            logger.info("-----> Verifying details...");
            Thread.sleep(1500); // Simulate work
            logger.info("-----> SUCCESS: Details for {} VERIFIED.", event.fileKey());

        } catch (Exception e) {
            logger.error("Error parsing SNS message or processing event", e);
        }
        logger.info("====================================================");
    }
}