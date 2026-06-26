package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Command(name = "list", description = "List MicroVMs", mixinStandardHelpOptions = true)
public class ListCommand implements Runnable {

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace (default: default)")
    String namespace;

    @Option(names = {"-A", "--all-namespaces"}, description = "List across all namespaces")
    boolean allNamespaces;

    @Inject
    KubernetesClient client;

    @Override
    public void run() {
        try {
            List<MicroVM> vms;
            if (allNamespaces) {
                vms = client.resources(MicroVM.class).inAnyNamespace().list().getItems();
            } else {
                vms = client.resources(MicroVM.class).inNamespace(namespace).list().getItems();
            }

            if (vms.isEmpty()) {
                System.out.println("No MicroVMs found.");
                return;
            }

            // Print header
            System.out.printf("%-20s %-12s %-36s %-12s %-8s %-8s%n",
                "NAME", "STATE", "VM-ID", "RUNTIME", "MEMORY", "AGE");

            // Print rows
            for (MicroVM vm : vms) {
                String vmName = vm.getMetadata().getName();
                String state = vm.getStatus() != null && vm.getStatus().getState() != null
                    ? vm.getStatus().getState().getValue() : "Unknown";
                String vmId = vm.getStatus() != null && vm.getStatus().getVmId() != null
                    ? vm.getStatus().getVmId() : "-";
                String runtime = vm.getSpec() != null && vm.getSpec().getRuntime() != null
                    ? vm.getSpec().getRuntime().getValue() : "-";
                String memory = vm.getSpec() != null && vm.getSpec().getMemoryMB() != null
                    ? vm.getSpec().getMemoryMB() + "Mi" : "-";
                String age = formatAge(vm.getMetadata().getCreationTimestamp());

                System.out.printf("%-20s %-12s %-36s %-12s %-8s %-8s%n",
                    vmName, state, vmId, runtime, memory, age);
            }
        } catch (KubernetesClientException e) {
            if (e.getCode() == 0) {
                System.err.println("Error: unable to connect to cluster");
                System.exit(1);
            }
            System.err.printf("Error: %s%n", e.getMessage());
            System.exit(1);
        }
    }

    private String formatAge(String creationTimestamp) {
        if (creationTimestamp == null) return "-";
        try {
            Instant created = Instant.parse(creationTimestamp);
            Duration age = Duration.between(created, Instant.now());
            if (age.toDays() > 0) return age.toDays() + "d";
            if (age.toHours() > 0) return age.toHours() + "h";
            if (age.toMinutes() > 0) return age.toMinutes() + "m";
            return age.toSeconds() + "s";
        } catch (Exception e) {
            return "-";
        }
    }
}
