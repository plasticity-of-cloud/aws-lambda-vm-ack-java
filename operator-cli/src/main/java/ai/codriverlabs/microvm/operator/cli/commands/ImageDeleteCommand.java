package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.model.MicroVMImage;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "delete", description = "Delete a MicroVM image", mixinStandardHelpOptions = true)
public class ImageDeleteCommand implements Runnable {

    @Option(names = {"--name"}, required = true, description = "Name of the MicroVMImage to delete")
    String name;

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace")
    String namespace;

    @Inject
    KubernetesClient client;

    @Override
    public void run() {
        try {
            MicroVMImage image = client.resources(MicroVMImage.class)
                .inNamespace(namespace).withName(name).get();

            if (image == null) {
                System.err.printf("MicroVMImage '%s' not found in namespace '%s'%n", name, namespace);
                System.exit(1);
                return;
            }

            client.resource(image).delete();
            System.out.printf("microvm-image/%s deleted%n", name);
        } catch (KubernetesClientException e) {
            System.err.println("Error deleting MicroVMImage: " + e.getMessage());
            System.exit(1);
        }
    }
}
