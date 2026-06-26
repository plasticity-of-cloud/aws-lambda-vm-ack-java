package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.model.MicroVMPool;
import ai.codriverlabs.microvm.operator.core.model.MicroVMPoolSpec;
import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "pool",
    description = "Manage MicroVM pools",
    mixinStandardHelpOptions = true,
    subcommands = {PoolCommand.PoolCreateCommand.class, PoolCommand.PoolScaleCommand.class}
)
public class PoolCommand {

    @Command(name = "create", description = "Create a new MicroVM pool", mixinStandardHelpOptions = true)
    public static class PoolCreateCommand implements Runnable {

        @Option(names = {"--name"}, required = true, description = "Pool name")
        String name;

        @Option(names = {"--replicas"}, defaultValue = "1", description = "Number of replicas (default: 1)")
        int replicas;

        @Option(names = {"--runtime"}, required = true, description = "Runtime for pool VMs")
        String runtime;

        @Option(names = {"--memory"}, defaultValue = "512", description = "Memory per VM in MB (default: 512)")
        int memoryMB;

        @Option(names = {"--vcpus"}, defaultValue = "2", description = "vCPUs per VM (default: 2)")
        int vcpus;

        @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace (default: default)")
        String namespace;

        @Inject
        KubernetesClient client;

        @Override
        public void run() {
            try {
                MicroVMPool pool = new MicroVMPool();
                pool.setMetadata(new ObjectMetaBuilder()
                    .withName(name)
                    .withNamespace(namespace)
                    .build());

                MicroVMPoolSpec spec = new MicroVMPoolSpec();
                spec.setReplicas(replicas);
                spec.setMaxSurge(1);
                spec.setMinReady(0);

                MicroVMSpec template = new MicroVMSpec();
                // template.setImageRef(DesiredState.fromValue(runtime));
                // template.setMemoryMB(memoryMB);
                // template.setVcpus(vcpus);
                spec.setTemplate(template);

                pool.setSpec(spec);

                client.resources(MicroVMPool.class)
                    .inNamespace(namespace)
                    .resource(pool)
                    .create();

                System.out.printf("MicroVMPool \"%s\" created with %d replicas%n", name, replicas);
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

    @Command(name = "scale", description = "Scale a MicroVM pool", mixinStandardHelpOptions = true)
    public static class PoolScaleCommand implements Runnable {

        @Parameters(index = "0", description = "Pool name")
        String name;

        @Option(names = {"--replicas"}, required = true, description = "Desired number of replicas")
        int replicas;

        @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace (default: default)")
        String namespace;

        @Inject
        KubernetesClient client;

        @Override
        public void run() {
            try {
                MicroVMPool pool = client.resources(MicroVMPool.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

                if (pool == null) {
                    System.err.printf("Error: MicroVMPool \"%s\" not found in namespace \"%s\"%n", name, namespace);
                    System.exit(1);
                }

                pool.getSpec().setReplicas(replicas);
                client.resources(MicroVMPool.class)
                    .inNamespace(namespace)
                    .resource(pool)
                    .patch();

                System.out.printf("MicroVMPool \"%s\" scaled to %d replicas%n", name, replicas);
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
}
