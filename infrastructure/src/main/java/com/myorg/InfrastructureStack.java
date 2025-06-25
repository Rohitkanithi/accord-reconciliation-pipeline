package com.myorg;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.EventType;
import software.amazon.awscdk.services.s3.notifications.SqsDestination;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

public class InfrastructureStack extends Stack {
    public InfrastructureStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // 1. SQS Queue for Ingestion
        Queue intakeQueue = Queue.Builder.create(this, "AccordIntakeQueue")
                .queueName("accord-intake-queue")
                .build();

        // 2. S3 Bucket for Document Uploads
        Bucket evidenceBucket = Bucket.Builder.create(this, "AccordEvidenceBucket").build();

        // 3. S3 Event Notification to SQS
        evidenceBucket.addEventNotification(
                EventType.OBJECT_CREATED,
                new SqsDestination(intakeQueue),
                software.amazon.awscdk.services.s3.NotificationKeyFilter.builder().suffix(".pdf").build()
        );

        // 4. SNS Topic for TransactionReceived Events
        Topic transactionReceivedTopic = Topic.Builder.create(this, "TransactionReceivedTopic")
                .topicName("accord-transaction-received-topic")
                .build();

        // 5. SSM Parameters to store resource names for our microservices
        StringParameter.Builder.create(this, "SqsQueueNameParameter")
                .parameterName("/accord/queues/intake-queue-name")
                .stringValue(intakeQueue.getQueueName())
                .build();

        StringParameter.Builder.create(this, "SnsTopicArnParameter")
                .parameterName("/accord/topics/transaction-received-arn")
                .stringValue(transactionReceivedTopic.getTopicArn())
                .build();
    }
}