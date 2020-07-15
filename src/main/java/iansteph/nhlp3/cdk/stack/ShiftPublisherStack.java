package iansteph.nhlp3.cdk.stack;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.sns.Subscription;
import software.amazon.awscdk.services.sns.SubscriptionProtocol;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueuePolicy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ShiftPublisherStack extends Stack {

    public ShiftPublisherStack(final Construct scope, final String id) {

        this(scope, id, null);
    }

    public ShiftPublisherStack(final Construct scope, final String id, final StackProps props) {

        super(scope, id, props);

        // SNS topic for publishing shift events
        final Topic shiftPublishingShiftTopic = Topic.Builder.create(this, "ShiftPublisherSnsTopic")
                .topicName("NHLP3-Shift-Events")
                .build();

        // Audit queue-- queue to be used for sanity checking, sns message publishing, serialization, and various other things
        final Queue shiftPublishingAuditQueue = Queue.Builder.create(this, "ShiftPublishingAuditQueue")
                .queueName("NHLP3-ShiftPublishing-Audit")
                .retentionPeriod(Duration.days(14))
                .build();
        final QueuePolicy queuePolicy = QueuePolicy.Builder.create(this, "ShiftPublishingAuditQueuePolicy")
                .queues(Collections.singletonList(shiftPublishingAuditQueue))
                .build();
        final Map<String, Object> conditions = new HashMap<>();
        final Map<String, String> conditionDetail = new HashMap<>();
        conditionDetail.put("aws:SourceArn", shiftPublishingShiftTopic.getTopicArn());
        conditions.put("ArnEquals", conditionDetail);
        final PolicyStatement shiftPublishingAuditQueuePolicyStatement = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .principals(Collections.singletonList(ServicePrincipal.Builder.create("sns.amazonaws.com").build()))
                .actions(Collections.singletonList("sqs:SendMessage"))
                .resources(Collections.singletonList(shiftPublishingAuditQueue.getQueueArn()))
                .conditions(conditions)
                .build();
        queuePolicy.getDocument().addStatements(shiftPublishingAuditQueuePolicyStatement);
        Subscription.Builder.create(this, "AuditQueueToShiftPublisherSnsTopicSubscription")
                .topic(shiftPublishingShiftTopic)
                .protocol(SubscriptionProtocol.SQS)
                .endpoint(shiftPublishingAuditQueue.getQueueArn())
                .rawMessageDelivery(true)
                .build();

        // S3 bucket to store the packaged artifacts for the ShiftPublisher lambda function
        final Bucket shiftPublisherPackagingAssetBucket = Bucket.Builder.create(this, "ShiftPublisherPackagingAssetBucket")
                .bucketName("nhlp3-shift-publisher-artifacts")
                .versioned(true)
                .build();

        // DynamoDB Table for the app
        final Table nhlp3AggregateTable = Table.Builder.create(this, "Nhlp3AggregateTable")
                .tableName("NHLP3-Aggregate")
                .partitionKey(Attribute.builder().name("PK").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("SK").type(AttributeType.STRING).build())
                .build();

        // Lambda function which publishes shift events to the SNS topic
        final Function shiftPublisherFunction = Function.Builder.create(this, "ShiftPublisherFunction")
                .functionName("NHLP3-ShiftPublisher-prod")
                .description("Publishes time on ice report data for both home and away teams for each NHL game")
                .code(Code.fromBucket(shiftPublisherPackagingAssetBucket, "ShiftPublisher-1.0.0.jar"))
                .handler("iansteph.nhlp3.shiftpublisher.handler.ShiftPublisherHandler::handleRequest")
                .memorySize(1024)
                .runtime(Runtime.JAVA_8)
                .timeout(Duration.seconds(60))
                .build();
        shiftPublishingShiftTopic.grantPublish(shiftPublisherFunction);
        nhlp3AggregateTable.grantReadWriteData(shiftPublisherFunction);
    }
}
