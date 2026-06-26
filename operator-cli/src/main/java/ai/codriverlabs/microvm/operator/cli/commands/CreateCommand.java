package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.enums.Runtime;
import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "create", description = "Create a new MicroVM", mixinStandardHelpOptions = true)
public class CreateCommand implements Runnable {

    @Option(names = {"--name"}, required = true, description = "Name of the MicroVM")
    String name;

    @Option(names = {"--runtime"}, required = true, description = "Runtime: java21, python3.12, nodejs20, custom")
    String runtime;

    @Option(names = {"--memory"}, defaultValue = "512", description = "Memory in MB (default: 512)")
    int memoryMB;

    @Option(names = {"--vcpus"}, defaultValue = "2", description = "Number of vCPUs (default: 2)")
    int vcpus;

    @Option(names = {"--timeout"}, defaultValue = "300", description = "Timeout in seconds (default: 300)")
    int timeout;

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace (default: default)")
    String namespace;

    @Inject
    KubernetesClient client;

    @Override
    public void run() {
        try {
            MicroVM microVM = new MicroVM();
            microVM.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build());

            MicroVMSpec spec = new MicroVMSpec();
            spec.setRuntime(Runtime.fromValue(runtime));
            spec.setMemoryMB(memoryMB);
            spec.setVcpus(vcpus);
            spec.setTimeoutSeconds(timeout);
            spec.setDesiredState(DesiredState.RUNNING);
            microVM.setSpec(spec);

            client.resources(MicroVM.class)
                .inNamespace(namespace)
                .resource(microVM)
                .create();

            System.out.printf("MicroVM \"%s\" created in namespace \"%s\"%n", name, namespace);
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
