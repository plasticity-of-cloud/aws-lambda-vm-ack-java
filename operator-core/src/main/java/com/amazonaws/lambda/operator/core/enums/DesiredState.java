package com.amazonaws.lambda.operator.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DesiredState {
    RUNNING("running"),
    PAUSED("paused"),
    STOPPED("stopped");

    private final String value;

    DesiredState(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static DesiredState fromValue(String value) {
        for (DesiredState d : values()) {
            if (d.value.equals(value)) return d;
        }
        throw new IllegalArgumentException("Unknown desired state: " + value);
    }
}
