package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "logs", description = "Stream logs from a MicroVM", mixinStandardHelpOptions = true)
public class LogsCommand implements Runnable {

    @Parameters(index = "0", description = "Name of the MicroVM")
    String name;

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace (default: default)")
    String namespace;

    @Option(names = {"-f", "--follow"}, description = "Follow log output")
    boolean follow;

    @Option(names = {"--tail"}, defaultValue = "-1", description = "Number of lines to show from end")
    int tail;

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

            if (vm.getStatus() == null || vm.getStatus().getMicroVmId() == null) {
                System.err.printf("Error: MicroVM \"%s\" has no VM ID (not yet created)%n", name);
                System.exit(1);
            }

            // In real implementation, this would stream logs from the MicroVM
            // via the AWS Lambda MicroVM API or a log aggregation service
            System.out.printf("Streaming logs for MicroVM \"%s\" (vmId: %s)...%n",
                name, vm.getStatus().getMicroVmId());
            System.out.println("(Log streaming not yet implemented - requires AWS Lambda MicroVM Log API)");

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
