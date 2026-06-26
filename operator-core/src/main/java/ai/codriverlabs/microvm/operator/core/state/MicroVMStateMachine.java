package ai.codriverlabs.microvm.operator.core.state;

import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;

import java.util.Map;
import java.util.Set;
import java.util.Collections;

import static ai.codriverlabs.microvm.operator.core.enums.MicroVMState.*;
import static java.util.Map.entry;

public class MicroVMStateMachine {

    private static final Map<MicroVMState, Set<MicroVMState>> VALID_TRANSITIONS = Map.ofEntries(
        entry(PENDING,      Set.of(CREATING)),
        entry(CREATING,     Set.of(RUNNING, FAILED)),
        entry(RUNNING,      Set.of(PAUSED, STOPPING, TERMINATING)),
        entry(PAUSED,       Set.of(RESUMING, TERMINATING)),
        entry(RESUMING,     Set.of(RUNNING, FAILED)),
        entry(STOPPING,     Set.of(STOPPED, FAILED)),
        entry(STOPPED,      Set.of(STARTING, TERMINATING)),
        entry(STARTING,     Set.of(RUNNING, FAILED)),
        entry(TERMINATING,  Set.of(TERMINATED)),
        entry(FAILED,       Set.of(CREATING, TERMINATING)),
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
