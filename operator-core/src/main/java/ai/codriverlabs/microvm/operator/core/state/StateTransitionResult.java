package ai.codriverlabs.microvm.operator.core.state;

import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;

public sealed interface StateTransitionResult
    permits StateTransitionResult.Valid, StateTransitionResult.Invalid {

    record Valid(MicroVMState from, MicroVMState to) implements StateTransitionResult {}
    record Invalid(MicroVMState from, MicroVMState attemptedTo, String reason) implements StateTransitionResult {}
}
