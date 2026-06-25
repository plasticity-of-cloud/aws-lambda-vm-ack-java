package com.amazonaws.lambda.operator.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroVMPoolStatus {
    private Integer readyReplicas;
    private Integer currentReplicas;
    private Integer desiredReplicas;
    private List<Condition> conditions = new ArrayList<>();
    private Long observedGeneration;

    public MicroVMPoolStatus() {}

    public Integer getReadyReplicas() { return readyReplicas; }
    public void setReadyReplicas(Integer readyReplicas) { this.readyReplicas = readyReplicas; }
    public Integer getCurrentReplicas() { return currentReplicas; }
    public void setCurrentReplicas(Integer currentReplicas) { this.currentReplicas = currentReplicas; }
    public Integer getDesiredReplicas() { return desiredReplicas; }
    public void setDesiredReplicas(Integer desiredReplicas) { this.desiredReplicas = desiredReplicas; }
    public List<Condition> getConditions() { return conditions; }
    public void setConditions(List<Condition> conditions) { this.conditions = conditions; }
    public Long getObservedGeneration() { return observedGeneration; }
    public void setObservedGeneration(Long observedGeneration) { this.observedGeneration = observedGeneration; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MicroVMPoolStatus s = (MicroVMPoolStatus) o;
        return Objects.equals(readyReplicas, s.readyReplicas) && Objects.equals(currentReplicas, s.currentReplicas) &&
               Objects.equals(desiredReplicas, s.desiredReplicas) && Objects.equals(conditions, s.conditions) &&
               Objects.equals(observedGeneration, s.observedGeneration);
    }

    @Override
    public int hashCode() { return Objects.hash(readyReplicas, currentReplicas, desiredReplicas, conditions, observedGeneration); }

    @Override
    public String toString() { return "MicroVMPoolStatus{ready=" + readyReplicas + ", current=" + currentReplicas + ", desired=" + desiredReplicas + "}"; }
}
