package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.model.MicroVMImage;
import ai.codriverlabs.microvm.operator.core.model.MicroVMImageStatus;
import ai.codriverlabs.microvm.operator.core.model.MicroVMImageVersionInfo;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "list", description = "List MicroVM images", mixinStandardHelpOptions = true)
public class ImageListCommand implements Runnable {

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace")
    String namespace;

    @Option(names = {"-A", "--all-namespaces"}, description = "List across all namespaces")
    boolean allNamespaces;

    @Inject
    KubernetesClient client;

    @Override
    public void run() {
        List<MicroVMImage> images;
        if (allNamespaces) {
            images = client.resources(MicroVMImage.class).inAnyNamespace().list().getItems();
        } else {
            images = client.resources(MicroVMImage.class).inNamespace(namespace).list().getItems();
        }

        if (images.isEmpty()) {
            System.out.println("No MicroVM images found.");
            return;
        }

        System.out.printf("%-20s %-12s %-10s %-40s %-8s%n", "NAME", "ACTIVE-VER", "LATEST-VER", "SOURCE", "STATE");
        for (MicroVMImage img : images) {
            MicroVMImageStatus status = img.getStatus();
            String activeVer = status != null && status.getActiveVersion() != null ? String.valueOf(status.getActiveVersion()) : "-";
            String latestVer = status != null && status.getLatestVersion() != null ? String.valueOf(status.getLatestVersion()) : "-";
            String source = img.getSpec() != null && img.getSpec().getSource() != null
                ? "s3://" + img.getSpec().getSource().getS3Bucket() + "/" + img.getSpec().getSource().getS3Key()
                : "-";
            if (source.length() > 40) source = source.substring(0, 39) + "…";
            String state = getLatestState(status);
            System.out.printf("%-20s %-12s %-10s %-40s %-8s%n", img.getMetadata().getName(), activeVer, latestVer, source, state);
        }
    }

    private String getLatestState(MicroVMImageStatus status) {
        if (status == null || status.getVersions() == null || status.getVersions().isEmpty()) return "-";
        MicroVMImageVersionInfo latest = status.getVersions().getFirst();
        return latest.getState() != null ? latest.getState() : "-";
    }
}
