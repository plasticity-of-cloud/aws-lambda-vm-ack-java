package ai.codriverlabs.microvm.operator.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * MicroVM lifecycle states as defined by the AWS Lambda MicroVMs API (2025-09-09).
 */
public enum MicroVMState {
    PENDING("Pending"),
    RUNNING("Running"),
    SUSPENDING("Suspending"),
    SUSPENDED("Suspended"),
    TERMINATING("Terminating"),
    TERMINATED("Terminated"),
    FAILED("Failed");

    private final String value;

    MicroVMState(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static MicroVMState fromValue(String value) {
        for (MicroVMState s : values()) {
            if (s.value.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown state: " + value);
    }
}
