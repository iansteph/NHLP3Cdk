package iansteph.nhlp3.cdk.stack;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
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
                .build();
    }
}
