package ai.codriverlabs.microvm.operator.controller.reconciler;

import ai.codriverlabs.microvm.operator.controller.metrics.OperatorMetrics;
import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.model.*;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reconciler for MicroVMReplicaSet resources. Manages scaling of child MicroVM resources
 * based on the pool's desired replicas, template, and scaling parameters.
 */
@ControllerConfiguration(
    finalizerName = "lambda.aws.amazon.com/microvmpool-finalizer"
)
public class MicroVMPoolReconciler implements Reconciler<MicroVMReplicaSet> {

    private static final Logger LOG = Logger.getLogger(MicroVMPoolReconciler.class);
    private static final String POOL_NAME_LABEL = "lambda.aws.amazon.com/pool-name";
    private static final int MAX_CHANGES_PER_CYCLE = 5;
    private static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    private final KubernetesClient kubernetesClient;
    private final OperatorMetrics metrics;

    @Inject
    public MicroVMPoolReconciler(KubernetesClient kubernetesClient, OperatorMetrics metrics) {
        this.kubernetesClient = kubernetesClient;
        this.metrics = metrics;
    }

    @Override
    public UpdateControl<MicroVMReplicaSet> reconcile(MicroVMReplicaSet resource, Context<MicroVMReplicaSet> context) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        LOG.infof("Reconciling MicroVMReplicaSet %s/%s", namespace, name);

        long startTime = System.nanoTime();
        String outcome = "success";

        try {
            MicroVMReplicaSetSpec spec = resource.getSpec();
            int desiredReplicas = spec.getReplicas() != null ? spec.getReplicas() : 0;
            int maxSurge = spec.getMaxSurge() != null ? spec.getMaxSurge() : 1;

            // List child MicroVMs by owner reference and pool label
            List<MicroVM> children = listChildren(namespace, name);
            int currentCount = children.size();

            // Scale up or down
            if (currentCount < desiredReplicas) {
                scaleUp(resource, children, desiredReplicas, maxSurge);
            } else if (currentCount > desiredReplicas) {
                scaleDown(resource, children, desiredReplicas);
            }

            // Update pool status
            updatePoolStatus(resource, children, desiredReplicas);

            return UpdateControl.patchStatus(resource).rescheduleAfter(RESYNC_PERIOD);
        } catch (Exception e) {
            outcome = "error";
            LOG.errorf(e, "Error reconciling MicroVMReplicaSet %s/%s", namespace, name);
            return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(10));
        } finally {
            long duration = System.nanoTime() - startTime;
            metrics.recordPoolReconciliation(outcome, duration);
        }
    }

    private List<MicroVM> listChildren(String namespace, String poolName) {
        return kubernetesClient.resources(MicroVM.class)
            .inNamespace(namespace)
            .withLabel(POOL_NAME_LABEL, poolName)
            .list()
            .getItems();
    }

    private void scaleUp(MicroVMReplicaSet pool, List<MicroVM> currentChildren, int desiredReplicas, int maxSurge) {
        int currentCount = currentChildren.size();
        int maxTotal = desiredReplicas + maxSurge;
        int toCreate = Math.min(desiredReplicas - currentCount, MAX_CHANGES_PER_CYCLE);
        // Don't exceed maxSurge over desired
        toCreate = Math.min(toCreate, maxTotal - currentCount);

        LOG.infof("Scaling up pool %s/%s: creating %d MicroVMs (current=%d, desired=%d)",
            pool.getMetadata().getNamespace(), pool.getMetadata().getName(), toCreate, currentCount, desiredReplicas);

        for (int i = 0; i < toCreate; i++) {
            createChildMicroVM(pool);
        }
    }

    private void scaleDown(MicroVMReplicaSet pool, List<MicroVM> currentChildren, int desiredReplicas) {
        int toDelete = Math.min(currentChildren.size() - desiredReplicas, MAX_CHANGES_PER_CYCLE);

        LOG.infof("Scaling down pool %s/%s: deleting %d MicroVMs (current=%d, desired=%d)",
            pool.getMetadata().getNamespace(), pool.getMetadata().getName(), toDelete, currentChildren.size(), desiredReplicas);

        // Sort by creation timestamp descending - delete most recently created first
        List<MicroVM> sortedByAge = currentChildren.stream()
            .sorted(Comparator.comparing(
                (MicroVM vm) -> vm.getMetadata().getCreationTimestamp() != null ?
                    vm.getMetadata().getCreationTimestamp() : "",
                Comparator.reverseOrder()))
            .collect(Collectors.toList());

        for (int i = 0; i < toDelete && i < sortedByAge.size(); i++) {
            MicroVM toRemove = sortedByAge.get(i);
            LOG.infof("Deleting MicroVM %s/%s for scale-down",
                toRemove.getMetadata().getNamespace(), toRemove.getMetadata().getName());
            kubernetesClient.resources(MicroVM.class)
                .inNamespace(toRemove.getMetadata().getNamespace())
                .withName(toRemove.getMetadata().getName())
                .delete();
        }
    }

    private void createChildMicroVM(MicroVMReplicaSet pool) {
        MicroVMSpec template = pool.getSpec().getTemplate();
        String poolName = pool.getMetadata().getName();
        String namespace = pool.getMetadata().getNamespace();

        // Create a new MicroVM from the template
        MicroVM child = new MicroVM();

        ObjectMeta meta = new ObjectMetaBuilder()
            .withGenerateName(poolName + "-")
            .withNamespace(namespace)
            .withLabels(Map.of(POOL_NAME_LABEL, poolName))
            .withOwnerReferences(List.of(buildOwnerReference(pool)))
            .build();
        child.setMetadata(meta);

        // Copy spec from template
        MicroVMSpec childSpec = new MicroVMSpec();
        childSpec.setImageRef(template.getImageRef());
        childSpec.setNetworkRef(template.getNetworkRef());
        childSpec.setDesiredState(DesiredState.RUNNING);
        childSpec.setMaxIdleDurationSeconds(template.getMaxIdleDurationSeconds());
        childSpec.setSuspendedDurationSeconds(template.getSuspendedDurationSeconds());
        childSpec.setAutoResumeEnabled(template.getAutoResumeEnabled());
        childSpec.setMaximumDurationSeconds(template.getMaximumDurationSeconds());
        childSpec.setTags(template.getTags());
        child.setSpec(childSpec);

        kubernetesClient.resources(MicroVM.class)
            .inNamespace(namespace)
            .resource(child)
            .create();
    }

    private OwnerReference buildOwnerReference(MicroVMReplicaSet pool) {
        return new OwnerReferenceBuilder()
            .withApiVersion(pool.getApiVersion())
            .withKind(pool.getKind())
            .withName(pool.getMetadata().getName())
            .withUid(pool.getMetadata().getUid())
            .withController(true)
            .withBlockOwnerDeletion(true)
            .build();
    }

    private void updatePoolStatus(MicroVMReplicaSet pool, List<MicroVM> children, int desiredReplicas) {
        MicroVMReplicaSetStatus status = pool.getStatus();
        if (status == null) {
            status = new MicroVMReplicaSetStatus();
            pool.setStatus(status);
        }

        // Count ready (Running) children
        long readyCount = children.stream()
            .filter(c -> c.getStatus() != null && c.getStatus().getState() == MicroVMState.RUNNING)
            .count();

        status.setReadyReplicas((int) readyCount);
        status.setCurrentReplicas(children.size());
        status.setDesiredReplicas(desiredReplicas);
        status.setObservedGeneration(pool.getMetadata().getGeneration());

        // Set pool condition
        Condition poolReady = new Condition(
            "Ready",
            readyCount >= desiredReplicas ? "True" : "False",
            readyCount >= desiredReplicas ? "AllReplicasReady" : "ScalingInProgress",
            String.format("%d/%d replicas ready", readyCount, desiredReplicas),
            Instant.now()
        );
        status.getConditions().removeIf(c -> "Ready".equals(c.getType()));
        status.getConditions().add(poolReady);
    }
}
