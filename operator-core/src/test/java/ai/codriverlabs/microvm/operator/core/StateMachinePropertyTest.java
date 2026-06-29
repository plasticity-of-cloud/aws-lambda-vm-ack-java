package ai.codriverlabs.microvm.operator.core;

import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.state.*;
import net.jqwik.api.*;

import java.util.*;

import static ai.codriverlabs.microvm.operator.core.enums.MicroVMState.*;

/**
 * Feature: kube-microvm-operator, Property 2: State Transition Validity
 * Validates: Requirements 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.14
 *
 * Feature: kube-microvm-operator, Property 3: Invalid State Transitions Are Rejected
 * Validates: Requirements 2.13, 2.14
 */
class StateMachinePropertyTest {

    private final MicroVMStateMachine stateMachine = new MicroVMStateMachine();

    // Valid transition pairs from the state machine implementation
    private static final List<MicroVMState[]> VALID_TRANSITIONS = List.of(
        new MicroVMState[]{PENDING, RUNNING},
        new MicroVMState[]{PENDING, FAILED},
        new MicroVMState[]{RUNNING, SUSPENDING},
        new MicroVMState[]{RUNNING, TERMINATING},
        new MicroVMState[]{SUSPENDING, SUSPENDED},
        new MicroVMState[]{SUSPENDING, FAILED},
        new MicroVMState[]{SUSPENDED, RUNNING},
        new MicroVMState[]{SUSPENDED, TERMINATING},
        new MicroVMState[]{TERMINATING, TERMINATED},
        new MicroVMState[]{FAILED, PENDING},
        new MicroVMState[]{FAILED, TERMINATING}
    );

    // Property 2: All valid transitions produce Valid result
    @Property(tries = 100)
    void validTransitionsProduceValidResult(@ForAll("validTransitionPairs") MicroVMState[] pair) {
        MicroVMState from = pair[0];
        MicroVMState to = pair[1];

        StateTransitionResult result = stateMachine.transition(from, to);

        assert result instanceof StateTransitionResult.Valid :
            "Expected Valid for " + from + " -> " + to + ", got Invalid";
        StateTransitionResult.Valid valid = (StateTransitionResult.Valid) result;
        assert valid.from() == from : "From state mismatch";
        assert valid.to() == to : "To state mismatch";
    }

    // Property 3: All invalid transitions produce Invalid result with non-empty reason
    @Property(tries = 100)
    void invalidTransitionsProduceInvalidResult(@ForAll("invalidTransitionPairs") MicroVMState[] pair) {
        MicroVMState from = pair[0];
        MicroVMState to = pair[1];

        StateTransitionResult result = stateMachine.transition(from, to);

        assert result instanceof StateTransitionResult.Invalid :
            "Expected Invalid for " + from + " -> " + to + ", got Valid";
        StateTransitionResult.Invalid invalid = (StateTransitionResult.Invalid) result;
        assert invalid.from() == from : "From state mismatch";
        assert invalid.attemptedTo() == to : "AttemptedTo state mismatch";
        assert invalid.reason() != null && !invalid.reason().isEmpty() : "Reason must not be empty";
    }

    // Additional: validTargets returns correct set for each state
    @Property(tries = 100)
    void validTargetsMatchTransitionTable(@ForAll("allStates") MicroVMState state) {
        Set<MicroVMState> targets = stateMachine.validTargets(state);

        // Every reported target must actually produce a Valid transition
        for (MicroVMState target : targets) {
            StateTransitionResult result = stateMachine.transition(state, target);
            assert result instanceof StateTransitionResult.Valid :
                "validTargets reported " + target + " for " + state + " but transition returned Invalid";
        }
    }

    @Provide
    Arbitrary<MicroVMState[]> validTransitionPairs() {
        return Arbitraries.of(VALID_TRANSITIONS);
    }

    @Provide
    Arbitrary<MicroVMState[]> invalidTransitionPairs() {
        // Generate all possible (from, to) pairs that are NOT in VALID_TRANSITIONS
        List<MicroVMState[]> invalidPairs = new ArrayList<>();
        Set<String> validSet = new HashSet<>();
        for (MicroVMState[] vt : VALID_TRANSITIONS) {
            validSet.add(vt[0].name() + "->" + vt[1].name());
        }
        for (MicroVMState from : MicroVMState.values()) {
            for (MicroVMState to : MicroVMState.values()) {
                if (!validSet.contains(from.name() + "->" + to.name())) {
                    invalidPairs.add(new MicroVMState[]{from, to});
                }
            }
        }
        return Arbitraries.of(invalidPairs);
    }

    @Provide
    Arbitrary<MicroVMState> allStates() {
        return Arbitraries.of(MicroVMState.values());
    }
}
