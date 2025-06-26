package com.accord.ingestionservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class MessageListener {

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

        try {
            S3Event s3Event = objectMapper.readValue(message.getPayload(), S3Event.class);
            S3EventRecord record = s3Event.records().get(0);

            String bucketName = record.s3().bucket().name();
            String encodedObjectKey = record.s3().object().key();
            String objectKey = URLDecoder.decode(encodedObjectKey, StandardCharsets.UTF_8);

            logger.info("-----> Bucket: {}, File: {}", bucketName, objectKey);

            // NEW LOGIC: Process the PDF stream directly from S3
            processPdfFromS3(bucketName, objectKey);

        } catch (Exception e) {
            logger.error("Failed to process message", e);
        }
        logger.info("====================================================");
    }

    // The try-with-resources statement ensures the S3 stream and PDF document are closed automatically
    private void processPdfFromS3(String bucketName, String objectKey) {
        logger.info("-----> Attempting to stream and parse PDF file from S3...");
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(getObjectRequest)) {

            long fileSize = s3ObjectStream.response().contentLength();

            // ✅ Read the stream into a byte array (PDFBox 3.x requires byte[] or SeekableInputStream)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int length;
            while ((length = s3ObjectStream.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }

            byte[] pdfBytes = baos.toByteArray();

            try (PDDocument pdfDocument = PDDocument.load(pdfBytes)) {
                int pageCount = pdfDocument.getNumberOfPages();
                logger.info("-----> File processed successfully! Size: {} bytes, Pages: {}", fileSize, pageCount);

                PDFTextStripper textStripper = new PDFTextStripper();
                String extractedText = textStripper.getText(pdfDocument);

                int snippetLength = Math.min(extractedText.length(), 300);
                String textSnippet = extractedText.substring(0, snippetLength).replaceAll("\\s+", " ").trim();

                logger.info("-----> SUCCESS: Extracted text snippet: '{}...'", textSnippet);
            }

        } catch (IOException e) {
            logger.error("Failed to read or parse PDF from S3 stream for key: {}", objectKey, e);
        }
    }
}