package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.model.MicroVMImage;
import ai.codriverlabs.microvm.operator.core.model.MicroVMImageSpec;
import ai.codriverlabs.microvm.operator.core.model.MicroVMImageSource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "create", description = "Create a new MicroVM image from S3 source", mixinStandardHelpOptions = true)
public class ImageCreateCommand implements Runnable {

    @Option(names = {"--name"}, required = true, description = "Name of the MicroVMImage")
    String name;

    @Option(names = {"--s3-bucket"}, required = true, description = "S3 bucket containing source zip")
    String s3Bucket;

    @Option(names = {"--s3-key"}, required = true, description = "S3 key of the source zip (Dockerfile + app code)")
    String s3Key;

    @Option(names = {"--base-image"}, description = "Base image ARN")
    String baseImageArn;

    @Option(names = {"--build-timeout"}, defaultValue = "600", description = "Build timeout in seconds (default: 600)")
    int buildTimeout;

    @Option(names = {"--auto-activate"}, defaultValue = "true", description = "Auto-activate on successful build (default: true)")
    boolean autoActivate;

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace")
    String namespace;

    @Inject
    KubernetesClient client;

    @Override
    public void run() {
        try {
            MicroVMImage image = new MicroVMImage();
            image.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build());

            MicroVMImageSpec spec = new MicroVMImageSpec();
            MicroVMImageSource source = new MicroVMImageSource();
            source.setS3Bucket(s3Bucket);
            source.setS3Key(s3Key);
            spec.setSource(source);
            spec.setBaseImageArn(baseImageArn);
            spec.setBuildTimeoutSeconds(buildTimeout);
            spec.setAutoActivate(autoActivate);
            image.setSpec(spec);

            client.resource(image).inNamespace(namespace).create();
            System.out.printf("microvm-image/%s created (source: s3://%s/%s)%n", name, s3Bucket, s3Key);
        } catch (KubernetesClientException e) {
            System.err.println("Error creating MicroVMImage: " + e.getMessage());
            System.exit(1);
        }
    }
}
