package iansteph.nhlp3.cdk.stack;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.sns.Topic;

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
