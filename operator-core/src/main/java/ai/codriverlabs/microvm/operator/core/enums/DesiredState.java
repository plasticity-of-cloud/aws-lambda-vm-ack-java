package ai.codriverlabs.microvm.operator.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Desired state for a MicroVM as supported by the AWS Lambda MicroVMs API.
 */
public enum DesiredState {
    RUNNING("Running"),
    SUSPENDED("Suspended"),
    TERMINATED("Terminated");

    private final String value;

    DesiredState(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static DesiredState fromValue(String value) {
        for (DesiredState d : values()) {
            if (d.value.equalsIgnoreCase(value)) return d;
        }
        throw new IllegalArgumentException("Unknown desired state: " + value);
    }
}
