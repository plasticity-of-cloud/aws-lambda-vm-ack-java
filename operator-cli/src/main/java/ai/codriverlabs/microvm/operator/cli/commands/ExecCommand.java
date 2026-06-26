package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "exec", description = "Execute a command in a MicroVM", mixinStandardHelpOptions = true)
public class ExecCommand implements Runnable {

    @Parameters(index = "0", description = "Name of the MicroVM")
    String name;

    @Parameters(index = "1..*", description = "Command to execute")
    List<String> command;

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

            if (vm.getStatus() == null || vm.getStatus().getMicroVmId() == null) {
                System.err.printf("Error: MicroVM \"%s\" has no VM ID (not yet created)%n", name);
                System.exit(1);
            }

            String commandStr = command != null ? String.join(" ", command) : "";
            // In real implementation, this would send the command to the MicroVM
            // via the AWS Lambda MicroVM Exec API
            System.out.printf("Executing in MicroVM \"%s\" (vmId: %s): %s%n",
                name, vm.getStatus().getMicroVmId(), commandStr);
            System.out.println("(Command execution not yet implemented - requires AWS Lambda MicroVM Exec API)");

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
