package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.aws.lambdamicrovms.LambdaMicrovmsClient;
import ai.codriverlabs.microvm.aws.lambdamicrovms.model.*;
import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.awssdk.regions.Region;

/**
 * kubectl microvm token — generate a short-lived JWE auth token for connecting to a MicroVM.
 *
 * Usage:
 *   kubectl microvm token --name my-vm
 *   kubectl microvm token --name my-vm --expires 60 --port 8080
 *
 * Pipe into curl:
 *   curl https://<endpoint>/ -H "X-aws-proxy-auth: $(kubectl microvm token --name my-vm)"
 */
@Command(name = "token",
        description = "Generate an auth token for connecting to a running MicroVM endpoint",
        mixinStandardHelpOptions = true)
public class TokenCommand implements Runnable {

    @Option(names = {"--name"}, required = true, description = "Name of the MicroVM CR")
    String name;

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace")
    String namespace;

    @Option(names = {"--expires"}, defaultValue = "30",
            description = "Token expiry in minutes (default: 30, max: 43200)")
    int expiresMinutes;

    @Option(names = {"--port"}, description = "Scope token to a specific port (default: all ports)")
    Integer port;

    @Option(names = {"--show-endpoint"}, description = "Also print the endpoint URL to stderr")
    boolean showEndpoint;

    @Option(names = {"--region"}, description = "AWS region (default: from MicroVM spec or us-east-1)")
    String region;

    @Inject
    KubernetesClient client;

    @Override
    public void run() {
        // 1. Resolve microvmId + endpoint from CR status
        MicroVM vm = client.resources(MicroVM.class).inNamespace(namespace).withName(name).get();
        if (vm == null) {
            System.err.printf("MicroVM '%s' not found in namespace '%s'%n", name, namespace);
            System.exit(1);
            return;
        }
        if (vm.getStatus() == null || vm.getStatus().getMicroVmId() == null) {
            System.err.printf("MicroVM '%s' has no microvmId in status (state: %s)%n",
                    name, vm.getStatus() != null ? vm.getStatus().getState() : "unknown");
            System.exit(1);
            return;
        }

        String microvmId = vm.getStatus().getMicroVmId();
        String endpoint = vm.getStatus().getEndpointUrl();
        String awsRegion = region != null ? region
                : (vm.getSpec().getRegion() != null ? vm.getSpec().getRegion() : "us-east-1");

        // 2. Build port specification
        PortSpecification portSpec = port != null
                ? PortSpecification.builder().port(port).build()
                : PortSpecification.builder().allPorts(Unit.builder().build()).build();

        // 3. Call CreateMicrovmAuthToken
        try (LambdaMicrovmsClient awsClient = LambdaMicrovmsClient.builder()
                .region(Region.of(awsRegion)).build()) {

            var response = awsClient.createMicrovmAuthToken(
                    CreateMicrovmAuthTokenRequest.builder()
                            .microvmIdentifier(microvmId)
                            .expirationInMinutes(expiresMinutes)
                            .allowedPorts(portSpec)
                            .build());

            if (showEndpoint && endpoint != null) {
                System.err.println("endpoint: " + endpoint);
            }

            // Print the token value for the X-aws-proxy-auth header
            String token = response.authToken().getOrDefault("X-aws-proxy-auth",
                    response.authToken().values().iterator().next());
            System.out.println(token);

        } catch (Exception e) {
            System.err.printf("Error creating auth token for MicroVM '%s': %s%n", name, e.getMessage());
            System.exit(1);
        }
    }
}
