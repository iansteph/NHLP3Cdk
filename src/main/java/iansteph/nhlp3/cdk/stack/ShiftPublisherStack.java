package iansteph.nhlp3.cdk.stack;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Permission;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.sns.Subscription;
import software.amazon.awscdk.services.sns.SubscriptionProtocol;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueuePolicy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class ShiftPublisherStack extends Stack {

    private final S3Client s3Client;

    public ShiftPublisherStack(final Construct scope, final String id) {

        this(scope, id, null, S3Client.create());
    }

    public ShiftPublisherStack(final Construct scope, final String id, final StackProps props, final S3Client s3Client) {
        super(scope, id, props);

        this.s3Client = s3Client;

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

        // Lambda function which publishes shift events to the SNS topic
        final ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .bucket("nhlp3-shift-publisher-artifacts")
                .build();
        final ListObjectsResponse listObjectsResponse = s3Client.listObjects(listObjectsRequest);
        final String lastModifiedS3ObjectCodeAsset = listObjectsResponse.contents().stream()
                .filter(s3Object -> s3Object.key().startsWith("ShiftPublisher-"))
                .sorted(Comparator.comparing(S3Object::lastModified).reversed())
                .collect(Collectors.toList()).get(0).key();
        System.out.println(format("LastModified S3Object key to use as Lambda function code asset: %s", lastModifiedS3ObjectCodeAsset));
        final IBucket shiftPublisherPackagingAssetBucket = Bucket.fromBucketName(this, "ShiftPublisherPackagingAssetBucket", "nhlp3-shift-publisher-artifacts");
        final Role shiftPublisherRole = Role.Builder.create(this, "ShiftPublisherFunctionServiceRole")
                .assumedBy(ServicePrincipal.Builder.create("lambda.amazonaws.com").build())
                .build();
        final PolicyStatement cloudWatchEventsAssumeRolePolicyStatement = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .principals(Collections.singletonList(ServicePrincipal.Builder.create("events.amazonaws.com").build()))
                .actions(Collections.singletonList("sts:AssumeRole"))
                .build();
        shiftPublisherRole.getAssumeRolePolicy().addStatements(cloudWatchEventsAssumeRolePolicyStatement);
        final Function shiftPublisherFunction = Function.Builder.create(this, "ShiftPublisherFunction")
                .functionName("NHLP3-ShiftPublisher-prod")
                .description("Publishes time on ice report data for both home and away teams for each NHL game")
                .role(shiftPublisherRole)
                .code(Code.fromBucket(shiftPublisherPackagingAssetBucket, lastModifiedS3ObjectCodeAsset))
                .handler("iansteph.nhlp3.shiftpublisher.handler.ShiftPublisherHandler::handleRequest")
                .memorySize(1024)
                .runtime(Runtime.JAVA_8)
                .timeout(Duration.seconds(60))
                .build();
        shiftPublishingShiftTopic.grantPublish(shiftPublisherFunction);
        Table.fromTableName(this, "NHLP3AggregateTable", "NHLP3-Aggregate").grantReadWriteData(shiftPublisherFunction);
        final Permission shiftPublisherTriggeredByScheduledCloudWatchEventRuleForGameIdPermission = Permission.builder()
                .action("lambda:InvokeFunction")
                .principal(ServicePrincipal.Builder.create("events.amazonaws.com").build())
                .sourceArn("arn:aws:events:us-east-1:627812672245:rule/GameId-*")
                .build();
        shiftPublisherFunction.addPermission("ShiftPublisherTriggeredByScheduledCloudWatchEventRuleForGameIdPermission",
                shiftPublisherTriggeredByScheduledCloudWatchEventRuleForGameIdPermission);
        // S3 Bucket to store the TOI Report version history as each game progresses
        final Bucket toiReportVersionHistoryBucket = Bucket.Builder.create(this, "TOIReportVersionHistoryBucket")
                .bucketName("nhlp3-shift-publisher-toi-report-version-history")
                .versioned(true)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .build();
        toiReportVersionHistoryBucket.grantWrite(shiftPublisherFunction);
    }
}
