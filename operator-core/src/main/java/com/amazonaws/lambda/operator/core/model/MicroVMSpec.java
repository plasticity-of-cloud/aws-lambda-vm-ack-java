package com.amazonaws.lambda.operator.core.model;

import com.amazonaws.lambda.operator.core.enums.DesiredState;
import com.amazonaws.lambda.operator.core.enums.Runtime;
import com.fasterxml.jackson.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroVMSpec {

    private String vmId;
    private Runtime runtime;
    private Integer memoryMB;
    private Integer vcpus;
    private Integer timeoutSeconds;
    private String networkRef;
    private String templateRef;
    private DesiredState desiredState;
    private String region;
    private Map<String, String> tags;

    @JsonIgnore
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    // Default constructor
    public MicroVMSpec() {}

    // All-args constructor
    public MicroVMSpec(Runtime runtime, Integer memoryMB, Integer vcpus, Integer timeoutSeconds,
                       String networkRef, String templateRef, DesiredState desiredState,
                       Map<String, String> tags) {
        this.runtime = runtime;
        this.memoryMB = memoryMB;
        this.vcpus = vcpus;
        this.timeoutSeconds = timeoutSeconds;
        this.networkRef = networkRef;
        this.templateRef = templateRef;
        this.desiredState = desiredState;
        this.tags = tags;
    }

    // Getters and setters for all fields
    public String getVmId() { return vmId; }
    public void setVmId(String vmId) { this.vmId = vmId; }

    public Runtime getRuntime() { return runtime; }
    public void setRuntime(Runtime runtime) { this.runtime = runtime; }

    public Integer getMemoryMB() { return memoryMB; }
    public void setMemoryMB(Integer memoryMB) { this.memoryMB = memoryMB; }

    public Integer getVcpus() { return vcpus; }
    public void setVcpus(Integer vcpus) { this.vcpus = vcpus; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public String getNetworkRef() { return networkRef; }
    public void setNetworkRef(String networkRef) { this.networkRef = networkRef; }

    public String getTemplateRef() { return templateRef; }
    public void setTemplateRef(String templateRef) { this.templateRef = templateRef; }

    public DesiredState getDesiredState() { return desiredState; }
    public void setDesiredState(DesiredState desiredState) { this.desiredState = desiredState; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) { additionalProperties.put(name, value); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MicroVMSpec that = (MicroVMSpec) o;
        return Objects.equals(vmId, that.vmId) &&
               runtime == that.runtime &&
               Objects.equals(memoryMB, that.memoryMB) &&
               Objects.equals(vcpus, that.vcpus) &&
               Objects.equals(timeoutSeconds, that.timeoutSeconds) &&
               Objects.equals(networkRef, that.networkRef) &&
               Objects.equals(templateRef, that.templateRef) &&
               desiredState == that.desiredState &&
               Objects.equals(region, that.region) &&
               Objects.equals(tags, that.tags) &&
               Objects.equals(additionalProperties, that.additionalProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vmId, runtime, memoryMB, vcpus, timeoutSeconds, networkRef, templateRef, desiredState, region, tags, additionalProperties);
    }

    @Override
    public String toString() {
        return "MicroVMSpec{" +
               "vmId='" + vmId + '\'' +
               ", runtime=" + runtime +
               ", memoryMB=" + memoryMB +
               ", vcpus=" + vcpus +
               ", timeoutSeconds=" + timeoutSeconds +
               ", networkRef='" + networkRef + '\'' +
               ", templateRef='" + templateRef + '\'' +
               ", desiredState=" + desiredState +
               ", region='" + region + '\'' +
               ", tags=" + tags +
               '}';
    }
}
