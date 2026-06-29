package ai.codriverlabs.microvm.operator.tests.integration;

import ai.codriverlabs.microvm.operator.controller.reconciler.MicroVMReplicaSetReconciler;
import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.model.*;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnableKubernetesMockClient(crud = true)
class MicroVMReplicaSetReconcilerIT {

    KubernetesClient client;
    MicroVMReplicaSetReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new MicroVMReplicaSetReconciler(client);
    }

    @Test
    @DisplayName("SCALE-UP: creates N children when currentReplicas < spec.replicas")
    void scaleUp_createsChildren() {
        var rs = testReplicaSet("my-rs", 3);
        client.resource(rs).create();

        reconciler.reconcile(rs, mockContext());

        List<MicroVM> children = client.resources(MicroVM.class).inNamespace("default")
                .withLabel(MicroVMReplicaSetReconciler.OWNER_LABEL, "my-rs").list().getItems();
        assertEquals(3, children.size(), "Should have created 3 child MicroVMs");
        // Each child should have ownerReference
        children.forEach(c -> assertFalse(c.getMetadata().getOwnerReferences().isEmpty()));
    }

    @Test
    @DisplayName("SCALE-DOWN: sets desiredState=Terminated on excess children")
    void scaleDown_terminatesExcess() {
        var rs = testReplicaSet("my-rs", 2);
        client.resource(rs).create();
        // Pre-create 4 running children
        for (int i = 0; i < 4; i++) {
            client.resource(runningChild("my-rs", "child-" + i)).create();
        }

        reconciler.reconcile(rs, mockContext());

        List<MicroVM> terminated = client.resources(MicroVM.class).inNamespace("default")
                .withLabel(MicroVMReplicaSetReconciler.OWNER_LABEL, "my-rs").list().getItems()
                .stream()
                .filter(c -> c.getSpec() != null
                        && DesiredState.TERMINATED == c.getSpec().getDesiredState())
                .toList();
        assertEquals(2, terminated.size(), "Should have set 2 children to TERMINATED");
    }

    @Test
    @DisplayName("HEALTH EVICTION: unexpected TERMINATED child (desired=Running) is deleted and replaced")
    void healthEviction_replacesUnexpectedlyTerminatedChild() {
        var rs = testReplicaSet("my-rs", 1);
        client.resource(rs).create();
        // Child is TERMINATED but spec.desiredState is RUNNING (unexpected termination)
        var terminated = runningChild("my-rs", "child-terminated");
        terminated.getSpec().setDesiredState(DesiredState.RUNNING); // not asked to terminate
        terminated.getStatus().setState(MicroVMState.TERMINATED);
        client.resource(terminated).create();

        reconciler.reconcile(rs, mockContext());

        List<MicroVM> children = client.resources(MicroVM.class).inNamespace("default")
                .withLabel(MicroVMReplicaSetReconciler.OWNER_LABEL, "my-rs").list().getItems();
        boolean unexpectedChildGone = children.stream()
                .noneMatch(c -> "child-terminated".equals(c.getMetadata().getName()));
        assertTrue(unexpectedChildGone, "Unexpectedly terminated child should have been evicted");
        assertEquals(1, children.size(), "A replacement child should have been created");
    }

    @Test
    @DisplayName("SUSPEND CASCADE: sets desiredState=Suspended on all children")
    void suspendCascade_patchesAllChildren() {
        var rs = testReplicaSet("my-rs", 2);
        rs.getSpec().setDesiredReplicaSetState("Suspended");
        client.resource(rs).create();
        for (int i = 0; i < 2; i++) {
            client.resource(runningChild("my-rs", "child-" + i)).create();
        }

        reconciler.reconcile(rs, mockContext());

        List<MicroVM> children = client.resources(MicroVM.class).inNamespace("default")
                .withLabel(MicroVMReplicaSetReconciler.OWNER_LABEL, "my-rs").list().getItems();
        children.forEach(c -> assertEquals(DesiredState.SUSPENDED, c.getSpec().getDesiredState(),
                "All children should be Suspended"));
    }

    @Test
    @DisplayName("STATUS: updates readyReplicas and currentReplicas correctly")
    void status_updatedAfterReconcile() {
        var rs = testReplicaSet("my-rs", 2);
        client.resource(rs).create();
        // Pre-create 2 running children
        for (int i = 0; i < 2; i++) {
            client.resource(runningChild("my-rs", "child-" + i)).create();
        }

        reconciler.reconcile(rs, mockContext());

        assertNotNull(rs.getStatus());
        assertEquals(2, rs.getStatus().getCurrentReplicas());
        assertEquals(2, rs.getStatus().getReadyReplicas());
        assertEquals(2, rs.getStatus().getDesiredReplicas());
    }

    // --- helpers ---

    private MicroVMReplicaSet testReplicaSet(String name, int replicas) {
        var rs = new MicroVMReplicaSet();
        rs.setMetadata(new ObjectMetaBuilder()
                .withName(name).withNamespace("default")
                .withUid("uid-" + name).withGeneration(1L).build());
        var spec = new MicroVMReplicaSetSpec();
        spec.setReplicas(replicas);
        var template = new MicroVMSpec();
        template.setImageRef("arn:aws:lambda:us-east-1:123:microvm-image:test");
        spec.setTemplate(template);
        rs.setSpec(spec);
        return rs;
    }

    private MicroVM runningChild(String rsName, String childName) {
        var vm = new MicroVM();
        vm.setMetadata(new ObjectMetaBuilder()
                .withName(childName).withNamespace("default")
                .addToLabels(MicroVMReplicaSetReconciler.OWNER_LABEL, rsName)
                .withCreationTimestamp("2026-06-29T00:00:00Z")
                .build());
        var spec = new MicroVMSpec();
        spec.setImageRef("arn:aws:lambda:us-east-1:123:microvm-image:test");
        spec.setDesiredState(DesiredState.RUNNING);
        vm.setSpec(spec);
        var status = new MicroVMStatus();
        status.setState(MicroVMState.RUNNING);
        vm.setStatus(status);
        return vm;
    }

    @SuppressWarnings("unchecked")
    private Context<MicroVMReplicaSet> mockContext() {
        Context<MicroVMReplicaSet> ctx = mock(Context.class);
        when(ctx.getClient()).thenReturn(client);
        return ctx;
    }
}
