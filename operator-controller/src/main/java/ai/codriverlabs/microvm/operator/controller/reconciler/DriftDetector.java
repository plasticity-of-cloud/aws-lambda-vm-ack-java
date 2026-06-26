package ai.codriverlabs.microvm.operator.controller.reconciler;

import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.state.MicroVMStateMachine;
import ai.codriverlabs.microvm.operator.core.state.StateTransitionResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Detects drift between the desired state declared in the CRD spec and the actual
 * state of the MicroVM as reported by the AWS API. Returns the appropriate action
 * to bring the actual state in line with the desired state.
 */
@ApplicationScoped
public class DriftDetector {

    private final MicroVMStateMachine stateMachine;

    @Inject
    public DriftDetector(MicroVMStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    /**
     * Compares the desired state from the CRD spec against the actual MicroVM state
     * and determines the action to take.
     *
     * @param desired the desired state declared in spec.desiredState
     * @param actual  the actual state from the AWS API
     * @return a DriftAction indicating what operation to perform, or NO_OP if aligned
     */
    public DriftResult detectDrift(DesiredState desired, MicroVMState actual) {
        if (desired == null || actual == null) {
            return DriftResult.error("Cannot detect drift: desired or actual state is null");
        }

        // Check if the actual state matches the desired state
        if (isAligned(desired, actual)) {
            return DriftResult.noOp();
        }

        // Check if the actual state is a transitional state moving toward the desired
        if (isTransitioning(desired, actual)) {
            return DriftResult.noOp();
        }

        // Determine the target state to transition to
        MicroVMState targetState = resolveTargetState(desired, actual);
        if (targetState == null) {
            return DriftResult.error("No valid transition path from " + actual.getValue() +
                " to desired state " + desired.getValue());
        }

        // Validate the transition is legal
        StateTransitionResult result = stateMachine.transition(actual, targetState);
        if (result instanceof StateTransitionResult.Valid valid) {
            return DriftResult.action(mapToAction(desired, actual), valid.to());
        }

        return DriftResult.error("Invalid transition from " + actual.getValue() +
            " to " + targetState.getValue() + ": no valid path exists");
    }

    /**
     * Checks whether the actual state is aligned with the desired state.
     */
    private boolean isAligned(DesiredState desired, MicroVMState actual) {
        return switch (desired) {
            case RUNNING -> actual == MicroVMState.RUNNING;
            case PAUSED -> actual == MicroVMState.PAUSED;
            case STOPPED -> actual == MicroVMState.STOPPED;
        };
    }

    /**
     * Checks if the actual state is a transitional state already moving toward the desired state.
     */
    private boolean isTransitioning(DesiredState desired, MicroVMState actual) {
        return switch (desired) {
            case RUNNING -> actual == MicroVMState.CREATING || actual == MicroVMState.STARTING || actual == MicroVMState.RESUMING;
            case PAUSED -> false; // No intermediate "pausing" state
            case STOPPED -> actual == MicroVMState.STOPPING;
        };
    }

    /**
     * Resolves the immediate target state to transition to based on the desired state
     * and current actual state.
     */
    private MicroVMState resolveTargetState(DesiredState desired, MicroVMState actual) {
        return switch (desired) {
            case RUNNING -> switch (actual) {
                case PENDING -> MicroVMState.CREATING;
                case PAUSED -> MicroVMState.RESUMING;
                case STOPPED -> MicroVMState.STARTING;
                case FAILED -> MicroVMState.CREATING;
                default -> null;
            };
            case PAUSED -> switch (actual) {
                case RUNNING -> MicroVMState.PAUSED;
                default -> null;
            };
            case STOPPED -> switch (actual) {
                case RUNNING -> MicroVMState.STOPPING;
                default -> null;
            };
        };
    }

    /**
     * Maps the drift to the corresponding AWS API action.
     */
    private DriftAction mapToAction(DesiredState desired, MicroVMState actual) {
        return switch (desired) {
            case RUNNING -> switch (actual) {
                case PENDING, FAILED -> DriftAction.CREATE;
                case PAUSED -> DriftAction.RESUME;
                case STOPPED -> DriftAction.START;
                default -> DriftAction.NO_OP;
            };
            case PAUSED -> DriftAction.PAUSE;
            case STOPPED -> DriftAction.STOP;
        };
    }

    /**
     * Represents the action to take to correct drift.
     */
    public enum DriftAction {
        CREATE,
        START,
        STOP,
        PAUSE,
        RESUME,
        NO_OP
    }

    /**
     * Result of drift detection, containing the action to take and the target state.
     */
    public sealed interface DriftResult {
        record ActionRequired(DriftAction action, MicroVMState targetState) implements DriftResult {}
        record NoOp() implements DriftResult {}
        record Error(String reason) implements DriftResult {}

        static DriftResult action(DriftAction action, MicroVMState targetState) {
            return new ActionRequired(action, targetState);
        }
        static DriftResult noOp() { return new NoOp(); }
        static DriftResult error(String reason) { return new Error(reason); }
    }
}
