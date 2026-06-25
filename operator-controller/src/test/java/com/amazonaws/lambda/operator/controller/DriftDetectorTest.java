package com.amazonaws.lambda.operator.controller;

import com.amazonaws.lambda.operator.core.enums.DesiredState;
import com.amazonaws.lambda.operator.core.enums.MicroVMState;
import com.amazonaws.lambda.operator.core.state.MicroVMStateMachine;
import com.amazonaws.lambda.operator.controller.reconciler.DriftDetector;
import com.amazonaws.lambda.operator.controller.reconciler.DriftDetector.DriftAction;
import com.amazonaws.lambda.operator.controller.reconciler.DriftDetector.DriftResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DriftDetectorTest {

    private DriftDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DriftDetector(new MicroVMStateMachine());
    }

    @Test
    void nullDesiredStateReturnsError() {
        DriftResult result = detector.detectDrift(null, MicroVMState.RUNNING);
        assertInstanceOf(DriftResult.Error.class, result);
    }

    @Test
    void nullActualStateReturnsError() {
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, null);
        assertInstanceOf(DriftResult.Error.class, result);
    }

    @Test
    void runningWithDesiredRunningReturnsNoOp() {
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, MicroVMState.RUNNING);
        assertInstanceOf(DriftResult.NoOp.class, result);
    }

    @Test
    void stoppedWithDesiredStoppedReturnsNoOp() {
        DriftResult result = detector.detectDrift(DesiredState.STOPPED, MicroVMState.STOPPED);
        assertInstanceOf(DriftResult.NoOp.class, result);
    }

    @Test
    void pausedWithDesiredPausedReturnsNoOp() {
        DriftResult result = detector.detectDrift(DesiredState.PAUSED, MicroVMState.PAUSED);
        assertInstanceOf(DriftResult.NoOp.class, result);
    }

    @Test
    void runningWithDesiredPausedReturnsPauseAction() {
        DriftResult result = detector.detectDrift(DesiredState.PAUSED, MicroVMState.RUNNING);
        assertInstanceOf(DriftResult.ActionRequired.class, result);
        DriftResult.ActionRequired action = (DriftResult.ActionRequired) result;
        assertEquals(DriftAction.PAUSE, action.action());
    }

    @Test
    void runningWithDesiredStoppedReturnsStopAction() {
        DriftResult result = detector.detectDrift(DesiredState.STOPPED, MicroVMState.RUNNING);
        assertInstanceOf(DriftResult.ActionRequired.class, result);
        DriftResult.ActionRequired action = (DriftResult.ActionRequired) result;
        assertEquals(DriftAction.STOP, action.action());
    }

    @Test
    void stoppedWithDesiredRunningReturnsStartAction() {
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, MicroVMState.STOPPED);
        assertInstanceOf(DriftResult.ActionRequired.class, result);
        DriftResult.ActionRequired action = (DriftResult.ActionRequired) result;
        assertEquals(DriftAction.START, action.action());
    }

    @Test
    void pausedWithDesiredRunningReturnsResumeAction() {
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, MicroVMState.PAUSED);
        assertInstanceOf(DriftResult.ActionRequired.class, result);
        DriftResult.ActionRequired action = (DriftResult.ActionRequired) result;
        assertEquals(DriftAction.RESUME, action.action());
    }

    @Test
    void pendingWithDesiredRunningReturnsCreateAction() {
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, MicroVMState.PENDING);
        assertInstanceOf(DriftResult.ActionRequired.class, result);
        DriftResult.ActionRequired action = (DriftResult.ActionRequired) result;
        assertEquals(DriftAction.CREATE, action.action());
    }

    @Test
    void failedWithDesiredRunningReturnsCreateAction() {
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, MicroVMState.FAILED);
        assertInstanceOf(DriftResult.ActionRequired.class, result);
        DriftResult.ActionRequired action = (DriftResult.ActionRequired) result;
        assertEquals(DriftAction.CREATE, action.action());
    }

    @Test
    void creatingWithDesiredRunningReturnsNoOp() {
        // CREATING is transitioning toward RUNNING, so no action needed
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, MicroVMState.CREATING);
        assertInstanceOf(DriftResult.NoOp.class, result);
    }

    @Test
    void startingWithDesiredRunningReturnsNoOp() {
        // STARTING is transitioning toward RUNNING
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, MicroVMState.STARTING);
        assertInstanceOf(DriftResult.NoOp.class, result);
    }

    @Test
    void resumingWithDesiredRunningReturnsNoOp() {
        // RESUMING is transitioning toward RUNNING
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, MicroVMState.RESUMING);
        assertInstanceOf(DriftResult.NoOp.class, result);
    }

    @Test
    void stoppingWithDesiredStoppedReturnsNoOp() {
        // STOPPING is transitioning toward STOPPED
        DriftResult result = detector.detectDrift(DesiredState.STOPPED, MicroVMState.STOPPING);
        assertInstanceOf(DriftResult.NoOp.class, result);
    }

    @Test
    void terminatingStateReturnsErrorOrNoOp() {
        // TERMINATING has no path to RUNNING - should return error
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, MicroVMState.TERMINATING);
        // Terminating has no resolve target for RUNNING, so it should be an error
        assertInstanceOf(DriftResult.Error.class, result);
    }

    @Test
    void terminatedStateReturnsError() {
        // TERMINATED cannot transition anywhere
        DriftResult result = detector.detectDrift(DesiredState.RUNNING, MicroVMState.TERMINATED);
        assertInstanceOf(DriftResult.Error.class, result);
    }
}
