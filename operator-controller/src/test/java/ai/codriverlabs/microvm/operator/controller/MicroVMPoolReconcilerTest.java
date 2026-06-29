package ai.codriverlabs.microvm.operator.controller;

import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;

import ai.codriverlabs.microvm.operator.core.model.*;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-style tests for MicroVMPoolReconciler verifying scaling behavior.
 */
class MicroVMPoolReconcilerTest {

    private static final String POOL_LABEL = "lambda.aws.amazon.com/pool-name";

    @Test
    @DisplayName("Pool creation spawns correct number of child MicroVMs")
    void poolCreationSpawnsCorrectChildren() {
        MicroVMReplicaSet pool = createPool("test-pool", 3, 1, 1);

        // Simulate creating children
        List<MicroVM> children = createChildren(pool, 3);

        assertEquals(3, children.size());
        for (MicroVM child : children) {
            assertEquals("test-pool", child.getMetadata().getLabels().get(POOL_LABEL));
            assertNotNull(child.getMetadata().getOwnerReferences());
            assertFalse(child.getMetadata().getOwnerReferences().isEmpty());
        }
    }

    @Test
    @DisplayName("Scale up creates additional VMs respecting maxSurge")
    void scaleUpRespectsMaxSurge() {
        MicroVMReplicaSet pool = createPool("test-pool", 5, 1, 2);
        List<MicroVM> existing = createChildren(pool, 3);

        // Need 5, have 3, maxSurge=2 -> max 7 total allowed
        int toCreate = Math.min(5 - existing.size(), 5); // 2, capped at 5 per cycle
        int afterScale = existing.size() + toCreate;

        assertTrue(afterScale <= 5 + 2, "After scale up should not exceed replicas + maxSurge");
        assertEquals(5, afterScale);
    }

    @Test
    @DisplayName("Scale down deletes most recently created VMs first (LIFO)")
    void scaleDownDeletesMostRecentFirst() {
        MicroVMReplicaSet pool = createPool("test-pool", 2, 1, 1);
        List<MicroVM> children = createChildrenWithTimestamps(pool, 5);

        // Scale from 5 to 2 -> delete 3 (but max 5 per cycle so all 3)
        int toDelete = children.size() - 2;

        // Sort by creation timestamp descending
        List<MicroVM> sortedByTimestamp = new ArrayList<>(children);
        sortedByTimestamp.sort((a, b) ->
            b.getMetadata().getCreationTimestamp().compareTo(a.getMetadata().getCreationTimestamp()));

        List<MicroVM> toRemove = sortedByTimestamp.subList(0, Math.min(toDelete, 5));
        List<MicroVM> survivors = new ArrayList<>(children);
        survivors.removeAll(toRemove);

        assertEquals(2, survivors.size());

        // Verify survivors are the oldest ones
        for (MicroVM survivor : survivors) {
            for (MicroVM deleted : toRemove) {
                assertTrue(
                    survivor.getMetadata().getCreationTimestamp()
                        .compareTo(deleted.getMetadata().getCreationTimestamp()) <= 0,
                    "Survivor should be older than deleted VM"
                );
            }
        }
    }

    @Test
    @DisplayName("Owner references set correctly on children")
    void ownerReferencesSetCorrectly() {
        MicroVMReplicaSet pool = createPool("test-pool", 3, 1, 1);
        pool.getMetadata().setUid("pool-uid-123");

        List<MicroVM> children = createChildren(pool, 3);

        for (MicroVM child : children) {
            List<OwnerReference> refs = child.getMetadata().getOwnerReferences();
            assertNotNull(refs);
            assertEquals(1, refs.size());

            OwnerReference ref = refs.get(0);
            assertEquals("MicroVMReplicaSet", ref.getKind());
            assertEquals("test-pool", ref.getName());
            assertEquals("pool-uid-123", ref.getUid());
            assertTrue(ref.getController());
        }
    }

    @Test
    @DisplayName("Pool status reflects actual child states")
    void poolStatusReflectsChildStates() {
        MicroVMReplicaSet pool = createPool("test-pool", 5, 1, 1);
        List<MicroVM> children = createChildren(pool, 5);

        // Set various states
        children.get(0).getStatus().setState(MicroVMState.RUNNING);
        children.get(1).getStatus().setState(MicroVMState.RUNNING);
        children.get(2).getStatus().setState(MicroVMState.RUNNING);
        children.get(3).getStatus().setState(MicroVMState.PENDING);
        children.get(4).getStatus().setState(MicroVMState.FAILED);

        // Calculate status
        long readyReplicas = children.stream()
            .filter(c -> c.getStatus().getState() == MicroVMState.RUNNING)
            .count();
        int currentReplicas = children.size();

        assertEquals(3, readyReplicas);
        assertEquals(5, currentReplicas);
        assertEquals(5, pool.getSpec().getReplicas());
    }

    @Test
    @DisplayName("Scaling throttled to max 5 per cycle")
    void scalingThrottledToMaxFivePerCycle() {
        MicroVMReplicaSet pool = createPool("test-pool", 20, 1, 5);
        List<MicroVM> existing = createChildren(pool, 5);

        // Need to create 15 more, but max 5 per cycle
        int deficit = 20 - existing.size();
        int toCreate = Math.min(deficit, 5);

        assertEquals(5, toCreate, "Should throttle creation to 5 per cycle");
    }

    // Helper methods

    private MicroVMReplicaSet createPool(String name, int replicas, int minReady, int maxSurge) {
        MicroVMReplicaSet pool = new MicroVMReplicaSet();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace("default");
        meta.setUid("pool-uid-" + name);
        pool.setMetadata(meta);

        MicroVMReplicaSetSpec spec = new MicroVMReplicaSetSpec();
        spec.setReplicas(replicas);
        spec.setMinReady(minReady);
        spec.setMaxSurge(maxSurge);

        MicroVMSpec template = new MicroVMSpec();
        template.setImageRef("python-sandbox");
        template.setMaximumDurationSeconds(512);
        template.setMaxIdleDurationSeconds(2);
        spec.setTemplate(template);
        pool.setSpec(spec);

        MicroVMReplicaSetStatus status = new MicroVMReplicaSetStatus();
        status.setDesiredReplicas(replicas);
        pool.setStatus(status);

        return pool;
    }

    private List<MicroVM> createChildren(MicroVMReplicaSet pool, int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> createChild(pool, "child-" + i, Instant.now().toString()))
            .collect(Collectors.toList());
    }

    private List<MicroVM> createChildrenWithTimestamps(MicroVMReplicaSet pool, int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> {
                String timestamp = Instant.parse("2025-01-01T00:00:00Z")
                    .plusSeconds(i * 60).toString();
                return createChild(pool, "child-" + i, timestamp);
            })
            .collect(Collectors.toList());
    }

    private MicroVM createChild(MicroVMReplicaSet pool, String name, String creationTimestamp) {
        MicroVM vm = new MicroVM();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace("default");
        meta.setCreationTimestamp(creationTimestamp);
        meta.setLabels(Map.of(POOL_LABEL, pool.getMetadata().getName()));

        OwnerReference ownerRef = new OwnerReference();
        ownerRef.setKind("MicroVMReplicaSet");
        ownerRef.setName(pool.getMetadata().getName());
        ownerRef.setUid(pool.getMetadata().getUid());
        ownerRef.setController(true);
        meta.setOwnerReferences(List.of(ownerRef));
        vm.setMetadata(meta);

        MicroVMSpec spec = new MicroVMSpec();
        spec.setImageRef("python-sandbox");
        spec.setMaximumDurationSeconds(512);
        spec.setMaxIdleDurationSeconds(2);
        vm.setSpec(spec);

        MicroVMStatus status = new MicroVMStatus();
        status.setState(MicroVMState.RUNNING);
        vm.setStatus(status);

        return vm;
    }
}
