package iansteph.nhlp3;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;

public class Nhlp3CdkStack extends Stack {
    public Nhlp3CdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public Nhlp3CdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here
    }
}
