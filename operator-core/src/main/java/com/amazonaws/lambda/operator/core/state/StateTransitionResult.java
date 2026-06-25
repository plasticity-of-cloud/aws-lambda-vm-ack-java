package com.amazonaws.lambda.operator.core.state;

import com.amazonaws.lambda.operator.core.enums.MicroVMState;

public sealed interface StateTransitionResult
    permits StateTransitionResult.Valid, StateTransitionResult.Invalid {

    record Valid(MicroVMState from, MicroVMState to) implements StateTransitionResult {}
    record Invalid(MicroVMState from, MicroVMState attemptedTo, String reason) implements StateTransitionResult {}
}
