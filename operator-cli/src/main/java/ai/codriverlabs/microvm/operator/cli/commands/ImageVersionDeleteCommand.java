package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.aws.lambdamicrovms.LambdaMicrovmsClient;
import ai.codriverlabs.microvm.aws.lambdamicrovms.model.*;
import ai.codriverlabs.microvm.operator.core.model.MicroVMImage;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.awssdk.regions.Region;

/**
 * kubectl microvm image version-delete — delete a specific MicroVM image version.
 *
 * Usage:
 *   kubectl microvm image version-delete --name my-image --version 1.0
 */
@Command(name = "version-delete",
        description = "Delete a specific MicroVM image version",
        mixinStandardHelpOptions = true)
public class ImageVersionDeleteCommand implements Runnable {

    @Option(names = {"--name"}, required = true, description = "Name of the MicroVMImage CR")
    String name;

    @Option(names = {"--version"}, required = true, description = "Image version to delete (e.g. 1.0)")
    String version;

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace")
    String namespace;

    @Option(names = {"--region"}, description = "AWS region")
    String region;

    @Inject
    KubernetesClient client;

    @Override
    public void run() {
        MicroVMImage image = client.resources(MicroVMImage.class)
                .inNamespace(namespace).withName(name).get();
        if (image == null) {
            System.err.printf("MicroVMImage '%s' not found in namespace '%s'%n", name, namespace);
            System.exit(1);
            return;
        }
        if (image.getStatus() == null || image.getStatus().getImageArn() == null) {
            System.err.printf("MicroVMImage '%s' has no imageArn in status%n", name);
            System.exit(1);
            return;
        }

        String imageArn = image.getStatus().getImageArn();
        String awsRegion = region != null ? region
                : (image.getSpec().getRegion() != null ? image.getSpec().getRegion() : "us-east-1");

        try (LambdaMicrovmsClient awsClient = LambdaMicrovmsClient.builder()
                .region(Region.of(awsRegion)).build()) {
            awsClient.deleteMicrovmImageVersion(DeleteMicrovmImageVersionRequest.builder()
                    .imageIdentifier(imageArn)
                    .imageVersion(version)
                    .build());
            System.out.printf("microvm-image/%s version %s deleted%n", name, version);
        } catch (Exception e) {
            System.err.printf("Error deleting image version: %s%n", e.getMessage());
            System.exit(1);
        }
    }
}
