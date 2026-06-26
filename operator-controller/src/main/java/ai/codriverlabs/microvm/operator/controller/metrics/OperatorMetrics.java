package ai.codriverlabs.microvm.operator.controller.metrics;

import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Prometheus metrics for the MicroVM operator.
 * Tracks reconciliation counts, durations, state transitions, and AWS API calls.
 */
@ApplicationScoped
public class OperatorMetrics {

    private final MeterRegistry registry;

    @Inject
    public OperatorMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records a reconciliation event with its outcome and duration.
     */
    public void recordReconciliation(String outcome, long durationNanos) {
        Counter.builder("microvm_reconciliations_total")
            .description("Total number of MicroVM reconciliations")
            .tag("outcome", outcome)
            .register(registry)
            .increment();

        Timer.builder("microvm_reconciliation_duration_seconds")
            .description("Duration of MicroVM reconciliation cycles")
            .tag("outcome", outcome)
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Records a state transition from one state to another.
     */
    public void recordStateTransition(MicroVMState fromState, MicroVMState toState) {
        Counter.builder("microvm_state_transitions_total")
            .description("Total number of MicroVM state transitions")
            .tag("from_state", fromState != null ? fromState.getValue() : "null")
            .tag("to_state", toState != null ? toState.getValue() : "null")
            .register(registry)
            .increment();
    }

    /**
     * Records an AWS API call with operation type and status.
     */
    public void recordAwsApiCall(String operation, String status) {
        Counter.builder("microvm_aws_api_calls_total")
            .description("Total number of AWS API calls")
            .tag("operation", operation)
            .tag("status", status)
            .register(registry)
            .increment();
    }

    /**
     * Records a pool reconciliation event.
     */
    public void recordPoolReconciliation(String outcome, long durationNanos) {
        Counter.builder("microvmpool_reconciliations_total")
            .description("Total number of MicroVMPool reconciliations")
            .tag("outcome", outcome)
            .register(registry)
            .increment();

        Timer.builder("microvmpool_reconciliation_duration_seconds")
            .description("Duration of MicroVMPool reconciliation cycles")
            .tag("outcome", outcome)
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
