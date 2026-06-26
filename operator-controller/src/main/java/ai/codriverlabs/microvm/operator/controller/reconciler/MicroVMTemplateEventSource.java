package ai.codriverlabs.microvm.operator.controller.reconciler;

import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import ai.codriverlabs.microvm.operator.core.model.MicroVMTemplate;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.stream.Collectors;

/**
 * Event source that watches MicroVMTemplate changes and triggers re-reconciliation
 * of MicroVMs that reference the changed template.
 */
public class MicroVMTemplateEventSource {

    private MicroVMTemplateEventSource() {}

    /**
     * Creates an InformerEventSource that maps MicroVMTemplate events to the
     * MicroVM resources that reference them via spec.templateRef.
     */
    public static InformerEventSource<MicroVMTemplate, MicroVM> create(EventSourceContext<MicroVM> context) {
        InformerEventSourceConfiguration<MicroVMTemplate> configuration =
            InformerEventSourceConfiguration.from(MicroVMTemplate.class, MicroVM.class)
                .withSecondaryToPrimaryMapper(template -> {
                    String templateName = template.getMetadata().getName();
                    String namespace = template.getMetadata().getNamespace();

                    // Find all MicroVMs in the same namespace that reference this template
                    KubernetesClient client = context.getClient();
                    return client.resources(MicroVM.class)
                        .inNamespace(namespace)
                        .list()
                        .getItems()
                        .stream()
                        .filter(vm -> templateName.equals(vm.getSpec().getTemplateRef()))
                        .map(vm -> new ResourceID(vm.getMetadata().getName(), vm.getMetadata().getNamespace()))
                        .collect(Collectors.toSet());
                })
                .build();

        return new InformerEventSource<>(configuration, context);
    }
}
