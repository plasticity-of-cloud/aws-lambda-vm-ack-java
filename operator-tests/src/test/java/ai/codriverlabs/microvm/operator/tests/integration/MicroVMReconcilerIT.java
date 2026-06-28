package ai.codriverlabs.microvm.operator.tests.integration;

import ai.codriverlabs.microvm.operator.controller.aws.*;
import ai.codriverlabs.microvm.operator.controller.metrics.OperatorMetrics;
import ai.codriverlabs.microvm.operator.controller.reconciler.DriftDetector;
import ai.codriverlabs.microvm.operator.controller.reconciler.MicroVMReconciler;
import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.model.*;
import ai.codriverlabs.microvm.operator.core.state.MicroVMStateMachine;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for MicroVMReconciler using Fabric8 mock Kubernetes API server (crud=true).
 */
@EnableKubernetesMockClient(crud = true)
class MicroVMReconcilerIT {

    KubernetesClient client;

    private MicroVMClient mockClient;
    private MicroVMReconciler reconciler;

    @BeforeEach
    void setUp() {
        mockClient = mock(MicroVMClient.class);
        OperatorMetrics metrics = new OperatorMetrics(new SimpleMeterRegistry());
        reconciler = new MicroVMReconciler(
                mockClient,
                new MicroVMStateMachine(),
                new DriftDetector(),
                metrics,
                client);
    }

    @Test
    @DisplayName("PENDING: reconciler calls runMicrovm and patches status with microvmId + RUNNING")
    void pending_callsRunMicrovmAndPatchesRunning() throws Exception {
        var vm = testMicroVM("test-vm", null);

        when(mockClient.runMicroVM(any())).thenReturn(CompletableFuture.completedFuture(
                new RunMicroVMResponse("mvm-abc123", "mvm-abc123.lambda-microvm.us-east-1.on.aws", "RUNNING")));

        var result = reconciler.reconcile(vm, mockContext());

        assertTrue(result.isPatchStatus());
        assertNotNull(vm.getStatus());
        assertEquals(MicroVMState.RUNNING, vm.getStatus().getState());
        assertEquals("mvm-abc123", vm.getStatus().getMicroVmId());
        assertEquals("mvm-abc123.lambda-microvm.us-east-1.on.aws", vm.getStatus().getEndpointUrl());
        verify(mockClient).runMicroVM(any());
    }

    @Test
    @DisplayName("RUNNING + desired SUSPENDED: drift detection triggers suspend")
    void drift_runningToSuspended_callsSuspend() throws Exception {
        var vm = testMicroVM("test-vm", MicroVMState.RUNNING);
        vm.getSpec().setDesiredState(DesiredState.SUSPENDED);
        vm.getStatus().setMicroVmId("mvm-abc123");

        when(mockClient.getMicroVM("mvm-abc123")).thenReturn(CompletableFuture.completedFuture(
                new DescribeMicroVMResponse("mvm-abc123", "RUNNING",
                        "mvm-abc123.lambda-microvm.us-east-1.on.aws", null, null)));
        when(mockClient.suspendMicroVM("mvm-abc123")).thenReturn(
                CompletableFuture.completedFuture(null));

        reconciler.reconcile(vm, mockContext());

        verify(mockClient).suspendMicroVM("mvm-abc123");
    }

    @Test
    @DisplayName("AWS throttle: retryable error keeps status unchanged")
    void throttle_retryableError_keepsPendingState() throws Exception {
        var vm = testMicroVM("test-vm", null);

        when(mockClient.runMicroVM(any())).thenReturn(CompletableFuture.failedFuture(
                new AwsApiException("Rate exceeded", AwsApiException.ErrorType.RETRYABLE, "req-1", 429)));

        var result = reconciler.reconcile(vm, mockContext());

        // Should reschedule, not crash
        assertNotNull(result);
        // State should stay PENDING (status initialized but not advanced)
        assertEquals(MicroVMState.PENDING, vm.getStatus().getState());
    }

    @Test
    @DisplayName("RUNNING: no-op when desired=Running and AWS state=RUNNING")
    void noOp_whenAligned() throws Exception {
        var vm = testMicroVM("test-vm", MicroVMState.RUNNING);
        vm.getSpec().setDesiredState(DesiredState.RUNNING);
        vm.getStatus().setMicroVmId("mvm-abc123");

        when(mockClient.getMicroVM("mvm-abc123")).thenReturn(CompletableFuture.completedFuture(
                new DescribeMicroVMResponse("mvm-abc123", "RUNNING",
                        "mvm-abc123.lambda-microvm.us-east-1.on.aws", null, null)));

        reconciler.reconcile(vm, mockContext());

        verify(mockClient, never()).suspendMicroVM(any());
        verify(mockClient, never()).terminateMicroVM(any());
        verify(mockClient, never()).runMicroVM(any());
    }

    @Test
    @DisplayName("DELETE: cleanup calls terminateMicrovm")
    void delete_callsTerminate() throws Exception {
        var vm = testMicroVM("test-vm", MicroVMState.RUNNING);
        vm.getStatus().setMicroVmId("mvm-abc123");

        when(mockClient.terminateMicroVM("mvm-abc123")).thenReturn(
                CompletableFuture.completedFuture(null));

        var deleteControl = reconciler.cleanup(vm, mockContext());

        assertTrue(deleteControl.isRemoveFinalizer());
        verify(mockClient).terminateMicroVM("mvm-abc123");
    }

    // --- helpers ---

    private MicroVM testMicroVM(String name, MicroVMState state) {
        var vm = new MicroVM();
        vm.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace("default")
                .withGeneration(1L)
                .build());
        var spec = new MicroVMSpec();
        spec.setImageRef("python-sandbox");
        spec.setDesiredState(DesiredState.RUNNING);
        spec.setMaximumDurationSeconds(3600);
        spec.setMaxIdleDurationSeconds(900);
        spec.setSuspendedDurationSeconds(300);
        vm.setSpec(spec);

        if (state != null) {
            var status = new MicroVMStatus();
            status.setState(state);
            status.setLastTransitionTime(Instant.now());
            vm.setStatus(status);
        }
        return vm;
    }

    @SuppressWarnings("unchecked")
    private Context<MicroVM> mockContext() {
        Context<MicroVM> ctx = mock(Context.class);
        when(ctx.getClient()).thenReturn(client);
        return ctx;
    }
}
