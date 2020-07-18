package iansteph.nhlp3.cdk;

import iansteph.nhlp3.cdk.stack.ShiftPublisherStack;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awssdk.services.s3.S3Client;

public class Nhlp3CdkApp {

    public static void main(final String[] args) {

        final S3Client s3Client = S3Client.create();
        App app = new App();
        final Environment commonEnvironment = Environment.builder()
                .region("us-east-1")
                .build();
        new ShiftPublisherStack(app, "ShiftPublisherStack", StackProps.builder().env(commonEnvironment).build(), s3Client);
        app.synth();
    }
}
