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

    // We only need the ObjectMapper now
    public VerificationListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // The message from an SNS->SQS subscription is a JSON string containing the SNS notification
    @SqsListener("${spring.cloud.aws.sqs.queue-name}")
    public void receiveMessage(String snsMessageJson) {
        logger.info("====================================================");
        logger.info("VERIFICATION SERVICE Received new message!");
        try {
            // 1. Parse the outer SNS/SQS message structure
            JsonNode rootNode = objectMapper.readTree(snsMessageJson);

            // 2. Extract the actual 'Message' payload, which is the JSON of our event
            String eventPayload = rootNode.get("Message").asText();

            // 3. Parse the inner JSON into our event object
            TransactionProcessedEvent event = objectMapper.readValue(eventPayload, TransactionProcessedEvent.class);

            logger.info("-----> Event parsed successfully for file: {}", event.fileKey());
            logger.info("-----> Verifying details for transaction involving {}...", event.fileKey());
            Thread.sleep(1500); // Simulate work
            logger.info("-----> SUCCESS: Details for {} are VERIFIED.", event.fileKey());

        } catch (Exception e) {
            logger.error("Error processing event in VerificationService", e);
        }
        logger.info("====================================================");
    }
}