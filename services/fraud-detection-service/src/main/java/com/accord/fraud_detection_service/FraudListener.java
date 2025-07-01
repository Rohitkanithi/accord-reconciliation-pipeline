package com.accord.fraud_detection_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class FraudListener {

    private static final Logger logger = LoggerFactory.getLogger(FraudListener.class);
    private final ObjectMapper objectMapper;
    private final Set<String> processedTransactions = Collections.synchronizedSet(new HashSet<>());

    public FraudListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SqsListener("${spring.cloud.aws.sqs.queue-name}")
    public void receiveMessage(String snsMessageJson) {
        logger.info("====================================================");
        logger.info("FRAUD SERVICE: Received new event!");
        try {
            JsonNode rootNode = objectMapper.readTree(snsMessageJson);
            String eventPayload = rootNode.get("Message").asText();
            TransactionProcessedEvent event = objectMapper.readValue(eventPayload, TransactionProcessedEvent.class);

            logger.info("-----> Event parsed successfully for file: {}", event.fileKey());

            String transactionId = event.fileKey();

            if (processedTransactions.add(transactionId)) {
                logger.info("-----> SUCCESS: New transaction. No fraud detected.");
            } else {
                logger.warn("-----> FRAUD WARNING: Duplicate transaction detected for file: {}", transactionId);
            }

        } catch (Exception e) {
            logger.error("Error processing event in FraudDetectionService", e);
        }
        logger.info("====================================================");
    }
}