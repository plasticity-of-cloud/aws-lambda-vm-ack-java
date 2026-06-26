package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.TimeUnit;

@Command(name = "delete", description = "Delete a MicroVM", mixinStandardHelpOptions = true)
public class DeleteCommand implements Runnable {

    @Parameters(index = "0", description = "Name of the MicroVM")
    String name;

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace (default: default)")
    String namespace;

    @Option(names = {"--wait"}, defaultValue = "true", description = "Wait for termination (default: true)")
    boolean wait;

    @Option(names = {"--timeout"}, defaultValue = "60", description = "Wait timeout in seconds (default: 60)")
    int timeout;

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

            // Delete the resource
            client.resources(MicroVM.class)
                .inNamespace(namespace)
                .withName(name)
                .delete();

            System.out.printf("MicroVM \"%s\" deletion initiated%n", name);

            if (wait) {
                System.out.printf("Waiting for termination (timeout: %ds)...%n", timeout);
                boolean deleted = client.resources(MicroVM.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .waitUntilCondition(
                        r -> r == null,
                        timeout,
                        TimeUnit.SECONDS
                    ) == null;

                if (deleted) {
                    System.out.printf("MicroVM \"%s\" successfully deleted%n", name);
                } else {
                    System.err.printf("Warning: MicroVM \"%s\" deletion timed out after %ds%n", name, timeout);
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
