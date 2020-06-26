package iansteph.nhlp3.cdk.stack;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;


public class ShiftPublisherStack extends Stack {

    public ShiftPublisherStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public ShiftPublisherStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // S3 bucket to store the packaged asset for the ShiftPublisher lambda function
        final Bucket shiftPublisherPackagingAssetBucket = new Bucket(this, "ShiftPublisherPackagingAssetBucket");

        // ShiftPublisher lambda function definition
        final Function lambdaFunction = Function.Builder.create(this, "ShiftPublisher")
                .runtime(Runtime.JAVA_8)
                .code(Code.fromBucket(shiftPublisherPackagingAssetBucket, "ShiftPublisher-1.0.0.jar"))
                .handler("iansteph.nhlp3.shiftpublisher.handler.ShiftPublisherHandler::handleRequest")
                .timeout(Duration.seconds(60))
                .build();
        shiftPublisherPackagingAssetBucket.grantReadWrite(lambdaFunction);
    }
}
