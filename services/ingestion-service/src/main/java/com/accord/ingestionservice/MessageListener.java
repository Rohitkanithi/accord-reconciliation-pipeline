package com.accord.ingestionservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder; // NEW IMPORT
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);

    private final ObjectMapper objectMapper;
    private final S3Client s3Client;
    private final SnsTemplate snsTemplate;
    private final String topicArn;

    public MessageListener(ObjectMapper objectMapper, S3Client s3Client, SnsTemplate snsTemplate,
                           @Value("${app.sns.topic-arn}") String topicArn) {
        this.objectMapper = objectMapper;
        this.s3Client = s3Client;
        this.snsTemplate = snsTemplate;
        this.topicArn = topicArn;
    }

    @SqsListener("${spring.cloud.aws.sqs.queue-name}")
    public void receiveMessage(Message<String> message) {
        logger.info("====================================================");
        logger.info("Received S3 event notification via SQS!");
        try {
            S3Event s3Event = objectMapper.readValue(message.getPayload(), S3Event.class);
            S3EventRecord record = s3Event.records().get(0);
            String bucketName = record.s3().bucket().name();
            String objectKey = URLDecoder.decode(record.s3().object().key(), StandardCharsets.UTF_8);
            logger.info("-----> Processing file: s3://{}/{}", bucketName, objectKey);
            processPdfAndPublishEvent(bucketName, objectKey);
        } catch (Exception e) {
            logger.error("Failed to process message", e);
        }
        logger.info("====================================================");
    }

    private void processPdfAndPublishEvent(String bucketName, String objectKey) {
        logger.info("-----> Attempting to stream, parse, and publish event...");
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getObjectRequest);
             PDDocument pdfDocument = PDDocument.load(s3Stream)) {

            long fileSize = s3Stream.response().contentLength();
            int pageCount = pdfDocument.getNumberOfPages();
            PDFTextStripper textStripper = new PDFTextStripper();
            String extractedText = textStripper.getText(pdfDocument);
            String textSnippet = extractedText.substring(0, Math.min(extractedText.length(), 500));
            logger.info("-----> PDF processed. Extracted {} characters.", extractedText.length());

            var eventToPublish = new TransactionProcessedEvent(bucketName, objectKey, fileSize, pageCount, textSnippet);

            // Correctly build a standard Spring Message and use the .send() method
            Message<TransactionProcessedEvent> snsMessage = MessageBuilder.withPayload(eventToPublish).build();
            snsTemplate.send(topicArn, snsMessage);

            logger.info("-----> SUCCESS: Published TransactionProcessedEvent to SNS topic!");

        } catch (Exception e) {
            logger.error("Failed to read, parse PDF, or publish to SNS for key: {}", objectKey, e);
        }
    }
}