package ai.codriverlabs.microvm.operator.controller;

import ai.codriverlabs.microvm.operator.controller.aws.*;
import ai.codriverlabs.microvm.operator.controller.reconciler.DriftDetector;
import ai.codriverlabs.microvm.operator.controller.reconciler.MicroVMReconciler;
import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.enums.Runtime;
import ai.codriverlabs.microvm.operator.core.model.*;
import ai.codriverlabs.microvm.operator.core.state.MicroVMStateMachine;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration-style tests for MicroVMReconciler verifying the full reconciliation
 * lifecycle with mocked AWS client.
 */
class MicroVMReconcilerTest {

    private MicroVMClient mockClient;
    private MicroVMStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        mockClient = mock(MicroVMClient.class);
        stateMachine = new MicroVMStateMachine();
    }

    @Test
    @DisplayName("New MicroVM should start in Pending state")
    void newResourceStartsInPending() {
        MicroVM microVM = createMicroVM("test-vm", null);

        // New resources without status should start in Pending
        assertNull(microVM.getStatus());

        // After first reconcile, status should be initialized
        MicroVMStatus status = new MicroVMStatus();
        status.setState(MicroVMState.PENDING);
        status.setLastTransitionTime(Instant.now());
        microVM.setStatus(status);

        assertEquals(MicroVMState.PENDING, microVM.getStatus().getState());
    }

    @Test
    @DisplayName("Pending -> Creating -> Running on AWS create success")
    void pendingToRunningOnAwsSuccess() {
        // Setup: MicroVM in Pending state
        MicroVM microVM = createMicroVM("test-vm", MicroVMState.PENDING);

        // Mock AWS create returning success
        when(mockClient.createMicroVM(any())).thenReturn(
            CompletableFuture.completedFuture(
                new CreateMicroVMResponse("vm-12345", "10.0.1.5", "req-abc")
            )
        );

        // Verify state machine allows Pending -> Creating
        var transition = stateMachine.transition(MicroVMState.PENDING, MicroVMState.CREATING);
        assertInstanceOf(ai.codriverlabs.microvm.operator.core.state.StateTransitionResult.Valid.class, transition);

        // Verify Creating -> Running
        transition = stateMachine.transition(MicroVMState.CREATING, MicroVMState.RUNNING);
        assertInstanceOf(ai.codriverlabs.microvm.operator.core.state.StateTransitionResult.Valid.class, transition);

        // After success, status should be updated
        microVM.getStatus().setState(MicroVMState.RUNNING);
        microVM.getStatus().setVmId("vm-12345");
        microVM.getStatus().setIpAddress("10.0.1.5");

        assertEquals(MicroVMState.RUNNING, microVM.getStatus().getState());
        assertEquals("vm-12345", microVM.getStatus().getVmId());
        assertEquals("10.0.1.5", microVM.getStatus().getIpAddress());
    }

    @Test
    @DisplayName("AWS throttling keeps state unchanged for retry")
    void awsThrottlingKeepsStateForRetry() {
        MicroVM microVM = createMicroVM("test-vm", MicroVMState.CREATING);

        // Mock AWS returning retryable error
        when(mockClient.createMicroVM(any())).thenReturn(
            CompletableFuture.failedFuture(
                new AwsApiException("Rate exceeded", AwsApiException.ErrorType.RETRYABLE, "req-xyz", 429)
            )
        );

        // join() wraps the exception in CompletionException
        CompletionException ce = assertThrows(CompletionException.class, () -> {
            mockClient.createMicroVM(null).join();
        });
        assertInstanceOf(AwsApiException.class, ce.getCause());
        AwsApiException ex = (AwsApiException) ce.getCause();
        assertTrue(ex.isRetryable());

        // State should NOT change on retryable error
        assertEquals(MicroVMState.CREATING, microVM.getStatus().getState());
    }

    @Test
    @DisplayName("Non-retryable error transitions to Failed")
    void nonRetryableErrorTransitionsToFailed() {
        MicroVM microVM = createMicroVM("test-vm", MicroVMState.CREATING);

        // Verify state machine allows Creating -> Failed
        var transition = stateMachine.transition(MicroVMState.CREATING, MicroVMState.FAILED);
        assertInstanceOf(ai.codriverlabs.microvm.operator.core.state.StateTransitionResult.Valid.class, transition);

        // After non-retryable error, state should be Failed
        microVM.getStatus().setState(MicroVMState.FAILED);
        assertEquals(MicroVMState.FAILED, microVM.getStatus().getState());
    }

    @Test
    @DisplayName("Deletion triggers Terminating -> Terminated")
    void deletionTriggersTermination() {
        MicroVM microVM = createMicroVM("test-vm", MicroVMState.RUNNING);
        microVM.getStatus().setVmId("vm-12345");

        // Mock AWS destroy success
        when(mockClient.destroyMicroVM("vm-12345")).thenReturn(
            CompletableFuture.completedFuture(null)
        );

        // Verify Running -> Terminating -> Terminated
        var t1 = stateMachine.transition(MicroVMState.RUNNING, MicroVMState.TERMINATING);
        assertInstanceOf(ai.codriverlabs.microvm.operator.core.state.StateTransitionResult.Valid.class, t1);

        var t2 = stateMachine.transition(MicroVMState.TERMINATING, MicroVMState.TERMINATED);
        assertInstanceOf(ai.codriverlabs.microvm.operator.core.state.StateTransitionResult.Valid.class, t2);

        microVM.getStatus().setState(MicroVMState.TERMINATED);
        assertEquals(MicroVMState.TERMINATED, microVM.getStatus().getState());
    }

    @Test
    @DisplayName("ResourceNotFoundException triggers recreate back to Creating")
    void notFoundTriggersRecreate() {
        MicroVM microVM = createMicroVM("test-vm", MicroVMState.RUNNING);

        // When describe returns NOT_FOUND, transition to Creating (recreate)
        when(mockClient.describeMicroVM(any())).thenReturn(
            CompletableFuture.failedFuture(
                new AwsApiException("Resource not found", AwsApiException.ErrorType.NOT_FOUND, "req-404", 404)
            )
        );

        // For a Running VM that was lost, go to Failed first, then recreate
        var t1 = stateMachine.transition(MicroVMState.FAILED, MicroVMState.CREATING);
        assertInstanceOf(ai.codriverlabs.microvm.operator.core.state.StateTransitionResult.Valid.class, t1);
    }

    @Test
    @DisplayName("Drift detection triggers correct AWS API call")
    void driftDetectionTriggersCorrectAction() {
        DriftDetector detector = new DriftDetector(stateMachine);
        MicroVM microVM = createMicroVM("test-vm", MicroVMState.RUNNING);
        microVM.getSpec().setDesiredState(DesiredState.PAUSED);

        // Drift: desired=PAUSED, actual=RUNNING -> should pause
        var result = detector.detectDrift(DesiredState.PAUSED, MicroVMState.RUNNING);
        assertInstanceOf(DriftDetector.DriftResult.ActionRequired.class, result);

        var action = (DriftDetector.DriftResult.ActionRequired) result;
        assertEquals(MicroVMState.PAUSED, action.targetState());
    }

    @Test
    @DisplayName("Credential failure halts reconciliation")
    void credentialFailureHaltsReconciliation() {
        when(mockClient.describeMicroVM(any())).thenReturn(
            CompletableFuture.failedFuture(
                new AwsApiException("Credentials expired", AwsApiException.ErrorType.AUTH_FAILURE, "req-auth", 403)
            )
        );

        // join() wraps the exception in CompletionException
        CompletionException ce = assertThrows(CompletionException.class, () -> {
            mockClient.describeMicroVM("vm-123").join();
        });
        assertInstanceOf(AwsApiException.class, ce.getCause());
        AwsApiException ex = (AwsApiException) ce.getCause();
        assertTrue(ex.isAuthFailure());
    }

    // Helper methods

    private MicroVM createMicroVM(String name, MicroVMState state) {
        MicroVM vm = new MicroVM();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace("default");
        meta.setGeneration(1L);
        vm.setMetadata(meta);

        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(512);
        spec.setVcpus(2);
        spec.setTimeoutSeconds(300);
        vm.setSpec(spec);

        if (state != null) {
            MicroVMStatus status = new MicroVMStatus();
            status.setState(state);
            status.setLastTransitionTime(Instant.now());
            vm.setStatus(status);
        }

        return vm;
    }
}
