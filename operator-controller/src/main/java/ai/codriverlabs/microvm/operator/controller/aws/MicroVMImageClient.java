package ai.codriverlabs.microvm.operator.controller.aws;

import ai.codriverlabs.microvm.aws.lambdamicrovms.LambdaMicrovmsAsyncClient;
import ai.codriverlabs.microvm.aws.lambdamicrovms.model.*;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;

import java.util.concurrent.CompletableFuture;

/**
 * AWS Lambda MicroVMs image operations.
 */
@ApplicationScoped
public class MicroVMImageClient {

    private final LambdaMicrovmsAsyncClient sdk;

    @Inject
    public MicroVMImageClient(
            @ConfigProperty(name = "aws.region", defaultValue = "us-east-1") String region) {
        this.sdk = LambdaMicrovmsAsyncClient.builder()
                .region(Region.of(region))
                .build();
    }

    public CompletableFuture<CreateMicrovmImageResponse> createImage(
            String name, String s3Uri, String baseImageArn, String buildRoleArn) {
        return sdk.createMicrovmImage(CreateMicrovmImageRequest.builder()
                .name(name)
                .codeArtifact(CodeArtifact.fromUri(s3Uri))
                .baseImageArn(baseImageArn)
                .buildRoleArn(buildRoleArn)
                .build());
    }

    public CompletableFuture<UpdateMicrovmImageResponse> updateImage(
            String imageIdentifier, String s3Uri, String baseImageArn, String buildRoleArn) {
        return sdk.updateMicrovmImage(UpdateMicrovmImageRequest.builder()
                .imageIdentifier(imageIdentifier)
                .codeArtifact(CodeArtifact.fromUri(s3Uri))
                .baseImageArn(baseImageArn)
                .buildRoleArn(buildRoleArn)
                .build());
    }

    public CompletableFuture<GetMicrovmImageResponse> getImage(String imageIdentifier) {
        return sdk.getMicrovmImage(GetMicrovmImageRequest.builder()
                .imageIdentifier(imageIdentifier)
                .build());
    }

    public CompletableFuture<GetMicrovmImageVersionResponse> getImageVersion(String imageIdentifier, String imageVersion) {
        return sdk.getMicrovmImageVersion(GetMicrovmImageVersionRequest.builder()
                .imageIdentifier(imageIdentifier)
                .imageVersion(imageVersion)
                .build());
    }

    public CompletableFuture<DeleteMicrovmImageResponse> deleteImage(String imageIdentifier) {
        return sdk.deleteMicrovmImage(DeleteMicrovmImageRequest.builder()
                .imageIdentifier(imageIdentifier)
                .build());
    }

    @PreDestroy
    void close() { sdk.close(); }
}
