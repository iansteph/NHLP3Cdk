package iansteph.nhlp3.cdk;

import software.amazon.awscdk.core.App;

import java.util.Arrays;

public class Nhlp3CdkApp {
    public static void main(final String[] args) {
        App app = new App();

        new Nhlp3CdkStack(app, "Nhlp3CdkStack");

        app.synth();
    }
}
