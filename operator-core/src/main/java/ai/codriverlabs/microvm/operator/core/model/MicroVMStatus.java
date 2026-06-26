package ai.codriverlabs.microvm.operator.core.model;

import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroVMStatus {
    private MicroVMState state;
    private String microVmId;
    private String endpointUrl;
    private String imageVersion;
    private List<Condition> conditions = new ArrayList<>();
    private Instant lastTransitionTime;
    private Long observedGeneration;

    public MicroVMStatus() {}

    public MicroVMState getState() { return state; }
    public void setState(MicroVMState state) { this.state = state; }
    public String getMicroVmId() { return microVmId; }
    public void setMicroVmId(String microVmId) { this.microVmId = microVmId; }
    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
    public String getImageVersion() { return imageVersion; }
    public void setImageVersion(String imageVersion) { this.imageVersion = imageVersion; }
    public List<Condition> getConditions() { return conditions; }
    public void setConditions(List<Condition> conditions) { this.conditions = conditions; }
    public Instant getLastTransitionTime() { return lastTransitionTime; }
    public void setLastTransitionTime(Instant lastTransitionTime) { this.lastTransitionTime = lastTransitionTime; }
    public Long getObservedGeneration() { return observedGeneration; }
    public void setObservedGeneration(Long observedGeneration) { this.observedGeneration = observedGeneration; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MicroVMStatus s = (MicroVMStatus) o;
        return state == s.state && Objects.equals(microVmId, s.microVmId) &&
               Objects.equals(endpointUrl, s.endpointUrl) &&
               Objects.equals(conditions, s.conditions) && Objects.equals(lastTransitionTime, s.lastTransitionTime) &&
               Objects.equals(observedGeneration, s.observedGeneration);
    }

    @Override
    public int hashCode() { return Objects.hash(state, microVmId, endpointUrl, conditions, lastTransitionTime, observedGeneration); }

    @Override
    public String toString() {
        return "MicroVMStatus{state=" + state + ", microVmId='" + microVmId + "', endpoint='" + endpointUrl + "'}";
    }
}
