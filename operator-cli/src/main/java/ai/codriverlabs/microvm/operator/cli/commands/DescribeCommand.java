package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "describe", description = "Describe a MicroVM", mixinStandardHelpOptions = true)
public class DescribeCommand implements Runnable {

    @Parameters(index = "0", description = "Name of the MicroVM")
    String name;

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace (default: default)")
    String namespace;

    @Inject
    KubernetesClient client;

    @Override
    public void run() {
        try {
            MicroVM vm = client.resources(MicroVM.class)
                .inNamespace(namespace)
                .withName(name)
                .get();

            if (vm == null) {
                System.err.printf("Error: MicroVM \"%s\" not found in namespace \"%s\"%n", name, namespace);
                System.exit(1);
            }

            System.out.printf("Name:       %s%n", vm.getMetadata().getName());
            System.out.printf("Namespace:  %s%n", vm.getMetadata().getNamespace());
            System.out.printf("Created:    %s%n", vm.getMetadata().getCreationTimestamp());
            System.out.println();

            System.out.println("Spec:");
            if (vm.getSpec() != null) {
                System.out.printf("  Runtime:    %s%n", vm.getSpec().getImageRef() != null ? vm.getSpec().getImageRef() : "-");
                System.out.printf("  Memory:     %s MB%n", vm.getSpec().getMaximumDurationSeconds() != null ? vm.getSpec().getMaximumDurationSeconds() : "-");
                System.out.printf("  vCPUs:      %s%n", vm.getSpec().getMaxIdleDurationSeconds() != null ? vm.getSpec().getMaxIdleDurationSeconds() : "-");
                System.out.printf("  Timeout:    %s s%n", vm.getSpec().getSuspendedDurationSeconds() != null ? vm.getSpec().getSuspendedDurationSeconds() : "-");
                System.out.printf("  Desired:    %s%n", vm.getSpec().getDesiredState() != null ? vm.getSpec().getDesiredState() : "-");
                System.out.printf("  NetworkRef: %s%n", vm.getSpec().getNetworkRef() != null ? vm.getSpec().getNetworkRef() : "-");
                System.out.printf("  Region:     %s%n", vm.getSpec().getRegion() != null ? vm.getSpec().getRegion() : "-");
            }
            System.out.println();

            System.out.println("Status:");
            if (vm.getStatus() != null) {
                System.out.printf("  State:      %s%n", vm.getStatus().getState() != null ? vm.getStatus().getState() : "-");
                System.out.printf("  VM ID:      %s%n", vm.getStatus().getMicroVmId() != null ? vm.getStatus().getMicroVmId() : "-");
                System.out.printf("  IP Address: %s%n", vm.getStatus().getEndpointUrl() != null ? vm.getStatus().getEndpointUrl() : "-");
                System.out.printf("  Generation: %s%n", vm.getStatus().getObservedGeneration() != null ? vm.getStatus().getObservedGeneration() : "-");

                if (vm.getStatus().getConditions() != null && !vm.getStatus().getConditions().isEmpty()) {
                    System.out.println("  Conditions:");
                    for (var condition : vm.getStatus().getConditions()) {
                        System.out.printf("    - Type: %s, Status: %s, Reason: %s%n",
                            condition.getType(), condition.getStatus(), condition.getReason());
                    }
                }
            }
        } catch (KubernetesClientException e) {
            if (e.getCode() == 0) {
                System.err.println("Error: unable to connect to cluster");
                System.exit(1);
            }
            System.err.printf("Error: %s%n", e.getMessage());
            System.exit(1);
        }
    }
}
