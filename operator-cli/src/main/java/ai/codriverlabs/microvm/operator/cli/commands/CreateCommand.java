package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.enums.DesiredState;

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

    @Option(names = {"--image"}, required = true, description = "MicroVMImage name")
    String imageRef;

    @Option(names = {"--max-duration"}, defaultValue = "28800", description = "Max duration seconds")
    int maxDuration;



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
            spec.setImageRef(imageRef);
            spec.setMaximumDurationSeconds(28800);
            // vcpus inherited from image;
            // timeout set via maximumDurationSeconds;
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
