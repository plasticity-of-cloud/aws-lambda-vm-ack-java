package com.amazonaws.lambda.operator.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroVMPoolSpec {
    private Integer replicas;
    private MicroVMSpec template;
    private Integer minReady;
    private Integer maxSurge;

    public MicroVMPoolSpec() {}

    public Integer getReplicas() { return replicas; }
    public void setReplicas(Integer replicas) { this.replicas = replicas; }
    public MicroVMSpec getTemplate() { return template; }
    public void setTemplate(MicroVMSpec template) { this.template = template; }
    public Integer getMinReady() { return minReady; }
    public void setMinReady(Integer minReady) { this.minReady = minReady; }
    public Integer getMaxSurge() { return maxSurge; }
    public void setMaxSurge(Integer maxSurge) { this.maxSurge = maxSurge; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MicroVMPoolSpec s = (MicroVMPoolSpec) o;
        return Objects.equals(replicas, s.replicas) && Objects.equals(template, s.template) &&
               Objects.equals(minReady, s.minReady) && Objects.equals(maxSurge, s.maxSurge);
    }

    @Override
    public int hashCode() { return Objects.hash(replicas, template, minReady, maxSurge); }

    @Override
    public String toString() { return "MicroVMPoolSpec{replicas=" + replicas + ", minReady=" + minReady + ", maxSurge=" + maxSurge + "}"; }
}
