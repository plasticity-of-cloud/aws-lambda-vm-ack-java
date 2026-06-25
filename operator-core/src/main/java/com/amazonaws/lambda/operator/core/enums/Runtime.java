package com.amazonaws.lambda.operator.core.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Runtime {
    JAVA21("java21"),
    PYTHON3_12("python3.12"),
    NODEJS20("nodejs20"),
    CUSTOM("custom");

    private final String value;

    Runtime(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static Runtime fromValue(String value) {
        for (Runtime r : values()) {
            if (r.value.equals(value)) return r;
        }
        throw new IllegalArgumentException("Unknown runtime: " + value);
    }
}
