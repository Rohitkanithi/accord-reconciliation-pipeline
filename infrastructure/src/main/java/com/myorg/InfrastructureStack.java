package com.myorg;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticache.CfnSubnetGroup;
import software.amazon.awscdk.services.elasticache.CfnCacheCluster;
import java.util.List;
import java.util.stream.Collectors;
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

        // --- A VPC for our services that need networking, like Redis ---
        Vpc vpc = Vpc.Builder.create(this, "AccordVPC")
                .maxAzs(2) // Use 2 Availability Zones for high availability
                .subnetConfiguration(List.of(
                        SubnetConfiguration.builder()
                                .name("private-isolated")
                                .subnetType(SubnetType.PRIVATE_ISOLATED)
                                .build()
                ))
                .build();

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

        // --- ElastiCache for Redis Cluster ---
        List<String> subnetIds = vpc.getIsolatedSubnets().stream().map(ISubnet::getSubnetId).collect(Collectors.toList());

        CfnSubnetGroup redisSubnetGroup = CfnSubnetGroup.Builder.create(this, "RedisSubnetGroup")
                .description("Subnet group for Redis")
                .subnetIds(subnetIds)
                .build();

        SecurityGroup redisSecurityGroup = SecurityGroup.Builder.create(this, "RedisSecurityGroup")
                .vpc(vpc)
                .description("Security group for Redis")
                .allowAllOutbound(true)
                .build();
        // NOTE: This rule allows any resource within the VPC to connect.
        redisSecurityGroup.addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(6379), "Allow Redis connections from within VPC");

        CfnCacheCluster redisCluster = CfnCacheCluster.Builder.create(this, "AccordRedisCluster")
                .cacheNodeType("cache.t2.micro")
                .engine("redis")
                .numCacheNodes(1)
                .vpcSecurityGroupIds(List.of(redisSecurityGroup.getSecurityGroupId()))
                .cacheSubnetGroupName(redisSubnetGroup.getRef())
                .build();

        // --- SQS Queue for the FraudDetectionService ---
        Queue fraudDetectionQueue = Queue.Builder.create(this, "FraudDetectionQueue")
                .queueName("fraud-detection-queue")
                .build();
        transactionReceivedTopic.addSubscription(new SqsSubscription(fraudDetectionQueue));

        // --- SSM Parameters for Redis and the new queue ---
        StringParameter.Builder.create(this, "FraudDetectionQueueUrlParameter")
                .parameterName("/accord/queues/fraud-detection-queue-url")
                .stringValue(fraudDetectionQueue.getQueueUrl())
                .build();

        StringParameter.Builder.create(this, "RedisAddress")
                .parameterName("/accord/redis/address")
                .stringValue(redisCluster.getAttrRedisEndpointAddress())
                .build();

        StringParameter.Builder.create(this, "RedisPort")
                .parameterName("/accord/redis/port")
                .stringValue(redisCluster.getAttrRedisEndpointPort())
                .build();
    }
}