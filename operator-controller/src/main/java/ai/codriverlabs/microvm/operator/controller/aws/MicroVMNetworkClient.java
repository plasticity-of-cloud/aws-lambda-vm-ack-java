package ai.codriverlabs.microvm.operator.controller.aws;

import ai.codriverlabs.microvm.aws.lambdacore.LambdaCoreAsyncClient;
import ai.codriverlabs.microvm.aws.lambdacore.model.*;
import ai.codriverlabs.microvm.operator.core.model.MicroVMNetworkSpec;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Wraps the generated LambdaCoreAsyncClient for Network Connector management.
 * All operations correspond to the lambda-core API (2026-04-30).
 */
@ApplicationScoped
public class MicroVMNetworkClient {

    private final LambdaCoreAsyncClient sdk;

    public MicroVMNetworkClient(
            @ConfigProperty(name = "microvm.aws.region", defaultValue = "us-east-1") String region) {
        this.sdk = LambdaCoreAsyncClient.builder()
                .region(Region.of(region))
                .build();
    }

    public CompletableFuture<CreateNetworkConnectorResponse> createConnector(
            String name, MicroVMNetworkSpec spec) {
        var vpcConfig = NetworkConnectorVpcEgressConfiguration.builder()
                .subnetIds(spec.getSubnetIds())
                .securityGroupIds(spec.getSecurityGroupIds())
                .networkProtocol(NetworkProtocol.fromValue(
                        spec.getNetworkProtocol() != null ? spec.getNetworkProtocol() : "IPv4"))
                .build();
        var req = CreateNetworkConnectorRequest.builder()
                .name(name)
                .configuration(NetworkConnectorConfiguration.builder()
                        .vpcEgressConfiguration(vpcConfig).build())
                .operatorRole(spec.getOperatorRoleArn());
        if (spec.getTags() != null) req.tags(spec.getTags());
        return sdk.createNetworkConnector(req.build());
    }

    public CompletableFuture<GetNetworkConnectorResponse> getConnector(String connectorArn) {
        return sdk.getNetworkConnector(GetNetworkConnectorRequest.builder()
                .identifier(connectorArn).build());
    }

    public CompletableFuture<UpdateNetworkConnectorResponse> updateConnector(
            String connectorArn, MicroVMNetworkSpec spec) {
        var vpcConfig = NetworkConnectorVpcEgressConfiguration.builder()
                .subnetIds(spec.getSubnetIds())
                .securityGroupIds(spec.getSecurityGroupIds())
                .networkProtocol(NetworkProtocol.fromValue(
                        spec.getNetworkProtocol() != null ? spec.getNetworkProtocol() : "IPv4"))
                .build();
        return sdk.updateNetworkConnector(UpdateNetworkConnectorRequest.builder()
                .identifier(connectorArn)
                .configuration(NetworkConnectorConfiguration.builder()
                        .vpcEgressConfiguration(vpcConfig).build())
                .build());
    }

    public CompletableFuture<DeleteNetworkConnectorResponse> deleteConnector(String connectorArn) {
        return sdk.deleteNetworkConnector(DeleteNetworkConnectorRequest.builder()
                .identifier(connectorArn).build());
    }

    public CompletableFuture<List<NetworkConnectorSummary>> listConnectors() {
        return sdk.listNetworkConnectors(ListNetworkConnectorsRequest.builder().build())
                .thenApply(r -> r.networkConnectors());
    }
}
