package com.amazonaws.lambda.operator.core.model;

import com.amazonaws.lambda.operator.core.enums.MicroVMState;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroVMStatus {
    private MicroVMState state;
    private String vmId;
    private String ipAddress;
    private List<Condition> conditions = new ArrayList<>();
    private Instant lastTransitionTime;
    private Long observedGeneration;

    public MicroVMStatus() {}

    public MicroVMState getState() { return state; }
    public void setState(MicroVMState state) { this.state = state; }
    public String getVmId() { return vmId; }
    public void setVmId(String vmId) { this.vmId = vmId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
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
        return state == s.state && Objects.equals(vmId, s.vmId) && Objects.equals(ipAddress, s.ipAddress) &&
               Objects.equals(conditions, s.conditions) && Objects.equals(lastTransitionTime, s.lastTransitionTime) &&
               Objects.equals(observedGeneration, s.observedGeneration);
    }

    @Override
    public int hashCode() { return Objects.hash(state, vmId, ipAddress, conditions, lastTransitionTime, observedGeneration); }

    @Override
    public String toString() {
        return "MicroVMStatus{state=" + state + ", vmId='" + vmId + "', ipAddress='" + ipAddress + "', observedGeneration=" + observedGeneration + "}";
    }
}
