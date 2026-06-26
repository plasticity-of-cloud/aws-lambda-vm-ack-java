package ai.codriverlabs.microvm.operator.controller.reconciler;

import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import ai.codriverlabs.microvm.operator.core.model.MicroVMNetwork;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Event source that watches MicroVMNetwork changes and triggers re-reconciliation
 * of MicroVMs that reference the changed network.
 */
public class MicroVMNetworkEventSource {

    private MicroVMNetworkEventSource() {}

    /**
     * Creates an InformerEventSource that maps MicroVMNetwork events to the
     * MicroVM resources that reference them via spec.networkRef.
     */
    public static InformerEventSource<MicroVMNetwork, MicroVM> create(EventSourceContext<MicroVM> context) {
        InformerEventSourceConfiguration<MicroVMNetwork> configuration =
            InformerEventSourceConfiguration.from(MicroVMNetwork.class, MicroVM.class)
                .withSecondaryToPrimaryMapper(network -> {
                    String networkName = network.getMetadata().getName();
                    String namespace = network.getMetadata().getNamespace();

                    // Find all MicroVMs in the same namespace that reference this network
                    KubernetesClient client = context.getClient();
                    return client.resources(MicroVM.class)
                        .inNamespace(namespace)
                        .list()
                        .getItems()
                        .stream()
                        .filter(vm -> networkName.equals(vm.getSpec().getNetworkRef()))
                        .map(vm -> new ResourceID(vm.getMetadata().getName(), vm.getMetadata().getNamespace()))
                        .collect(Collectors.toSet());
                })
                .build();

        return new InformerEventSource<>(configuration, context);
    }
}
