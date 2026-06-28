package ai.codriverlabs.microvm.operator.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MicroVMImageVersionInfo {

    /** Version string (e.g. "1.0", "2.0") */
    private String version;
    /** Version build state: PENDING, IN_PROGRESS, SUCCESSFUL, FAILED */
    private String state;
    /** Version activation: ACTIVE, INACTIVE */
    private String status;
    private String builtAt;
    private String startedAt;
    private String failureReason;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBuiltAt() { return builtAt; }
    public void setBuiltAt(String builtAt) { this.builtAt = builtAt; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
