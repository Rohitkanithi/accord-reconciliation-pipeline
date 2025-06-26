package com.myorg;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.EventType;
import software.amazon.awscdk.services.s3.notifications.SqsDestination;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

public class InfrastructureStack extends Stack {
    public InfrastructureStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);


        Queue intakeQueue = Queue.Builder.create(this, "AccordIntakeQueue")
                .queueName("accord-intake-queue")
                .build();

        Bucket evidenceBucket = Bucket.Builder.create(this, "AccordEvidenceBucket")
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();

        evidenceBucket.addEventNotification(
                EventType.OBJECT_CREATED,
                new SqsDestination(intakeQueue),
                software.amazon.awscdk.services.s3.NotificationKeyFilter.builder().suffix(".pdf").build()
        );

        Topic transactionReceivedTopic = Topic.Builder.create(this, "TransactionReceivedTopic")
                .topicName("accord-transaction-received-topic")
                .build();

        StringParameter.Builder.create(this, "SqsQueueNameParameter")
                .parameterName("/accord/queues/intake-queue-name")
                .stringValue(intakeQueue.getQueueName())
                .build();

        StringParameter.Builder.create(this, "SnsTopicArnParameter")
                .parameterName("/accord/topics/transaction-received-arn")
                .stringValue(transactionReceivedTopic.getTopicArn())
                .build();

        // VERIFICATION SERVICE ---

        // 1. Create a dedicated SQS queue for the Verification service
        Queue verificationQueue = Queue.Builder.create(this, "VerificationQueue")
                .queueName("verification-queue")
                .build();

        // 2. Subscribe this new queue to the existing SNS topic.
        transactionReceivedTopic.addSubscription(new SqsSubscription(verificationQueue));

        // 3. Store the new queue's URL in SSM Parameter Store for the new service to use.
        StringParameter.Builder.create(this, "VerificationQueueUrlParameter")
                .parameterName("/accord/queues/verification-queue-url")
                .stringValue(verificationQueue.getQueueUrl())
                .build();
    }
}