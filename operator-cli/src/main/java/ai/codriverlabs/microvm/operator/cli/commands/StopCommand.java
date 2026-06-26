package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "stop", description = "Stop a running MicroVM", mixinStandardHelpOptions = true)
public class StopCommand implements Runnable {

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

            vm.getSpec().setDesiredState(DesiredState.STOPPED);
            client.resources(MicroVM.class)
                .inNamespace(namespace)
                .resource(vm)
                .patch();

            System.out.printf("MicroVM \"%s\" stopping%n", name);
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
