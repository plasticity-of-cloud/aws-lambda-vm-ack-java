package ai.codriverlabs.microvm.operator.core.state;

import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;

import java.util.Map;
import java.util.Set;
import java.util.Collections;

import static ai.codriverlabs.microvm.operator.core.enums.MicroVMState.*;
import static java.util.Map.entry;

/**
 * State machine matching the AWS Lambda MicroVMs lifecycle (API 2025-09-09).
 * 
 * PENDING → RUNNING → SUSPENDING → SUSPENDED → RUNNING (resume)
 *                   → TERMINATING → TERMINATED
 * FAILED → PENDING (retry) or TERMINATING
 */
public class MicroVMStateMachine {

    private static final Map<MicroVMState, Set<MicroVMState>> VALID_TRANSITIONS = Map.ofEntries(
        entry(PENDING,      Set.of(RUNNING, FAILED)),
        entry(RUNNING,      Set.of(SUSPENDING, TERMINATING)),
        entry(SUSPENDING,   Set.of(SUSPENDED, FAILED)),
        entry(SUSPENDED,    Set.of(RUNNING, TERMINATING)),
        entry(TERMINATING,  Set.of(TERMINATED)),
        entry(FAILED,       Set.of(PENDING, TERMINATING)),
        entry(TERMINATED,   Set.of())
    );

    public StateTransitionResult transition(MicroVMState current, MicroVMState target) {
        Set<MicroVMState> validTargets = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (validTargets.contains(target)) {
            return new StateTransitionResult.Valid(current, target);
        }
        return new StateTransitionResult.Invalid(current, target,
            "Cannot transition from " + current.getValue() + " to " + target.getValue() +
            ". Valid targets: " + validTargets);
    }

    public Set<MicroVMState> validTargets(MicroVMState current) {
        return Collections.unmodifiableSet(VALID_TRANSITIONS.getOrDefault(current, Set.of()));
    }
}
