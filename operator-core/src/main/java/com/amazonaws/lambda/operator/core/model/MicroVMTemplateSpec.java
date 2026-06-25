package com.amazonaws.lambda.operator.core.model;

import com.amazonaws.lambda.operator.core.enums.Runtime;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroVMTemplateSpec {
    private Runtime runtime;
    private Integer memoryMB;
    private Integer vcpus;
    private Integer timeoutSeconds;
    private Map<String, String> environment;
    private Map<String, String> labels;

    public MicroVMTemplateSpec() {}

    public Runtime getRuntime() { return runtime; }
    public void setRuntime(Runtime runtime) { this.runtime = runtime; }
    public Integer getMemoryMB() { return memoryMB; }
    public void setMemoryMB(Integer memoryMB) { this.memoryMB = memoryMB; }
    public Integer getVcpus() { return vcpus; }
    public void setVcpus(Integer vcpus) { this.vcpus = vcpus; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Map<String, String> getEnvironment() { return environment; }
    public void setEnvironment(Map<String, String> environment) { this.environment = environment; }
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MicroVMTemplateSpec s = (MicroVMTemplateSpec) o;
        return runtime == s.runtime && Objects.equals(memoryMB, s.memoryMB) && Objects.equals(vcpus, s.vcpus) &&
               Objects.equals(timeoutSeconds, s.timeoutSeconds) && Objects.equals(environment, s.environment) &&
               Objects.equals(labels, s.labels);
    }

    @Override
    public int hashCode() { return Objects.hash(runtime, memoryMB, vcpus, timeoutSeconds, environment, labels); }

    @Override
    public String toString() { return "MicroVMTemplateSpec{runtime=" + runtime + ", memoryMB=" + memoryMB + ", vcpus=" + vcpus + "}"; }
}
