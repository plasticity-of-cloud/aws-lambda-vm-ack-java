package com.amazonaws.lambda.operator.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MicroVMState {
    PENDING("Pending"),
    CREATING("Creating"),
    RUNNING("Running"),
    PAUSED("Paused"),
    RESUMING("Resuming"),
    STOPPED("Stopped"),
    STARTING("Starting"),
    STOPPING("Stopping"),
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
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown state: " + value);
    }
}
