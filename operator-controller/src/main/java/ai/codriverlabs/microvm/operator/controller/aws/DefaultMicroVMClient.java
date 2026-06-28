package ai.codriverlabs.microvm.operator.controller.aws;

import ai.codriverlabs.microvm.aws.lambdamicrovms.LambdaMicrovmsAsyncClient;
import ai.codriverlabs.microvm.aws.lambdamicrovms.model.*;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class DefaultMicroVMClient implements MicroVMClient {

    private final LambdaMicrovmsAsyncClient sdk;

    @Inject
    public DefaultMicroVMClient(
            @ConfigProperty(name = "aws.region", defaultValue = "us-east-1") String region) {
        this.sdk = LambdaMicrovmsAsyncClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Override
    public CompletableFuture<RunMicroVMResponse> runMicroVM(RunMicroVMRequest request) {
        var builder = RunMicrovmRequest.builder()
                .imageIdentifier(request.imageIdentifier())
                .executionRoleArn(request.executionRoleArn())
                .runHookPayload(request.runHookPayload());

        if (request.imageVersion() != null) {
            builder.imageVersion(request.imageVersion());
        }
        if (request.maximumDurationSeconds() != null) {
            builder.maximumDurationInSeconds(request.maximumDurationSeconds());
        }
        if (request.maxIdleDurationSeconds() != null || request.suspendedDurationSeconds() != null
                || request.autoResumeEnabled() != null) {
            builder.idlePolicy(IdlePolicy.builder()
                    .maxIdleDurationSeconds(request.maxIdleDurationSeconds())
                    .suspendedDurationSeconds(request.suspendedDurationSeconds())
                    .autoResumeEnabled(request.autoResumeEnabled())
                    .build());
        }
        if (request.ingressNetworkConnectors() != null && !request.ingressNetworkConnectors().isEmpty()) {
            builder.ingressNetworkConnectors(request.ingressNetworkConnectors());
        }
        if (request.egressNetworkConnectors() != null && !request.egressNetworkConnectors().isEmpty()) {
            builder.egressNetworkConnectors(request.egressNetworkConnectors());
        }

        return sdk.runMicrovm(builder.build())
                .thenApply(r -> new RunMicroVMResponse(r.microvmId(), r.stateAsString(), r.endpoint()));
    }

    @Override
    public CompletableFuture<DescribeMicroVMResponse> getMicroVM(String microvmId) {
        return sdk.getMicrovm(GetMicrovmRequest.builder().microvmIdentifier(microvmId).build())
                .thenApply(r -> new DescribeMicroVMResponse(
                        r.microvmId(), r.stateAsString(), r.endpoint(), null, r.imageVersion()));
    }

    @Override
    public CompletableFuture<Void> suspendMicroVM(String microvmId) {
        return sdk.suspendMicrovm(SuspendMicrovmRequest.builder().microvmIdentifier(microvmId).build())
                .thenApply(r -> null);
    }

    @Override
    public CompletableFuture<Void> resumeMicroVM(String microvmId) {
        return sdk.resumeMicrovm(ResumeMicrovmRequest.builder().microvmIdentifier(microvmId).build())
                .thenApply(r -> null);
    }

    @Override
    public CompletableFuture<Void> terminateMicroVM(String microvmId) {
        return sdk.terminateMicrovm(TerminateMicrovmRequest.builder().microvmIdentifier(microvmId).build())
                .thenApply(r -> null);
    }

    @Override
    public CompletableFuture<Map<String, String>> createAuthToken(
            String microvmId, int expirationMinutes, boolean allPorts) {
        var portsBuilder = PortSpecification.builder();
        if (allPorts) {
            portsBuilder.allPorts(Unit.builder().build());
        } else {
            portsBuilder.port(8080);
        }
        return sdk.createMicrovmAuthToken(CreateMicrovmAuthTokenRequest.builder()
                .microvmIdentifier(microvmId)
                .expirationInMinutes(expirationMinutes)
                .allowedPorts(portsBuilder.build())
                .build())
                .thenApply(r -> r.authToken());
    }

    @PreDestroy
    void close() {
        sdk.close();
    }
}
