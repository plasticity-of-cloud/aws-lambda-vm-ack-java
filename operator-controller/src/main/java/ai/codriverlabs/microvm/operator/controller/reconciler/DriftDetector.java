package ai.codriverlabs.microvm.operator.controller.reconciler;

import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;

/**
 * Detects drift between desired state (CR spec) and actual AWS state.
 * Aligned with AWS Lambda MicroVMs API lifecycle (no stop/pause — only suspend/resume/terminate).
 */
public class DriftDetector {

    public enum DriftAction {
        RECREATE, SUSPEND, RESUME, TERMINATE, NO_OP
    }

    public sealed interface DriftResult {
        record NoOp(String reason) implements DriftResult {}
        record ActionRequired(DriftAction action, MicroVMState targetState) implements DriftResult {}
        record Error(String reason) implements DriftResult {}
    }

    public DriftResult detectDrift(DesiredState desired, MicroVMState actual) {
        if (desired == null) return new DriftResult.Error("desiredState is null");
        if (actual == null) return new DriftResult.Error("actual state is null");

        return switch (desired) {
            case RUNNING -> detectRunningDrift(actual);
            case SUSPENDED -> detectSuspendedDrift(actual);
            case TERMINATED -> detectTerminatedDrift(actual);
        };
    }

    private DriftResult detectRunningDrift(MicroVMState actual) {
        return switch (actual) {
            case RUNNING -> new DriftResult.NoOp("Aligned: Running");
            case PENDING -> new DriftResult.NoOp("Transitional: provisioning in progress");
            case SUSPENDING -> new DriftResult.NoOp("Transitional: suspending (will resume after)");
            case SUSPENDED -> new DriftResult.ActionRequired(DriftAction.RESUME, MicroVMState.RUNNING);
            case TERMINATED -> new DriftResult.ActionRequired(DriftAction.RECREATE, MicroVMState.PENDING);
            case FAILED -> new DriftResult.ActionRequired(DriftAction.RECREATE, MicroVMState.PENDING);
            case TERMINATING -> new DriftResult.Error("Cannot resume: termination in progress");
        };
    }

    private DriftResult detectSuspendedDrift(MicroVMState actual) {
        return switch (actual) {
            case SUSPENDED -> new DriftResult.NoOp("Aligned: Suspended");
            case SUSPENDING -> new DriftResult.NoOp("Transitional: suspending");
            case RUNNING -> new DriftResult.ActionRequired(DriftAction.SUSPEND, MicroVMState.SUSPENDING);
            case PENDING -> new DriftResult.NoOp("Transitional: provisioning (will suspend after running)");
            case TERMINATED -> new DriftResult.Error("Cannot suspend a terminated MicroVM");
            case TERMINATING -> new DriftResult.Error("Cannot suspend: termination in progress");
            case FAILED -> new DriftResult.Error("Cannot suspend a failed MicroVM");
        };
    }

    private DriftResult detectTerminatedDrift(MicroVMState actual) {
        return switch (actual) {
            case TERMINATED -> new DriftResult.NoOp("Aligned: Terminated");
            case TERMINATING -> new DriftResult.NoOp("Transitional: terminating");
            case RUNNING -> new DriftResult.ActionRequired(DriftAction.TERMINATE, MicroVMState.TERMINATING);
            case SUSPENDED -> new DriftResult.ActionRequired(DriftAction.TERMINATE, MicroVMState.TERMINATING);
            case PENDING -> new DriftResult.ActionRequired(DriftAction.TERMINATE, MicroVMState.TERMINATING);
            case SUSPENDING -> new DriftResult.NoOp("Transitional: will terminate after suspend completes");
            case FAILED -> new DriftResult.ActionRequired(DriftAction.TERMINATE, MicroVMState.TERMINATING);
        };
    }
}
