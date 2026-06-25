package com.amazonaws.lambda.operator.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Condition {
    private String type;
    private String status; // "True", "False", "Unknown"
    private String reason;
    private String message;
    private Instant lastTransitionTime;

    public Condition() {}

    public Condition(String type, String status, String reason, String message, Instant lastTransitionTime) {
        this.type = type;
        this.status = status;
        this.reason = reason;
        this.message = message;
        this.lastTransitionTime = lastTransitionTime;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getLastTransitionTime() { return lastTransitionTime; }
    public void setLastTransitionTime(Instant lastTransitionTime) { this.lastTransitionTime = lastTransitionTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Condition c = (Condition) o;
        return Objects.equals(type, c.type) && Objects.equals(status, c.status) &&
               Objects.equals(reason, c.reason) && Objects.equals(message, c.message) &&
               Objects.equals(lastTransitionTime, c.lastTransitionTime);
    }

    @Override
    public int hashCode() { return Objects.hash(type, status, reason, message, lastTransitionTime); }

    @Override
    public String toString() {
        return "Condition{type='" + type + "', status='" + status + "', reason='" + reason + "', message='" + message + "'}";
    }
}
