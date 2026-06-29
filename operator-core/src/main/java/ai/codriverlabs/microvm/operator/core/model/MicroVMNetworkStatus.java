package ai.codriverlabs.microvm.operator.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroVMNetworkStatus {
    private String connectorArn;
    private String connectorId;
    /** PENDING | ACTIVE | INACTIVE | FAILED | DELETING | DELETE_FAILED */
    private String connectorState;
    private String stateReason;
    private String stateReasonCode;
    private Long observedGeneration;
    private List<Condition> conditions;

    public MicroVMNetworkStatus() {}

    public String getConnectorArn() { return connectorArn; }
    public void setConnectorArn(String connectorArn) { this.connectorArn = connectorArn; }
    public String getConnectorId() { return connectorId; }
    public void setConnectorId(String connectorId) { this.connectorId = connectorId; }
    public String getConnectorState() { return connectorState; }
    public void setConnectorState(String connectorState) { this.connectorState = connectorState; }
    public String getStateReason() { return stateReason; }
    public void setStateReason(String stateReason) { this.stateReason = stateReason; }
    public String getStateReasonCode() { return stateReasonCode; }
    public void setStateReasonCode(String stateReasonCode) { this.stateReasonCode = stateReasonCode; }
    public Long getObservedGeneration() { return observedGeneration; }
    public void setObservedGeneration(Long observedGeneration) { this.observedGeneration = observedGeneration; }
    public List<Condition> getConditions() { return conditions; }
    public void setConditions(List<Condition> conditions) { this.conditions = conditions; }
}
