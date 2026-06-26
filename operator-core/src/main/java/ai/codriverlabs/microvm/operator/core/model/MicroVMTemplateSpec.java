package ai.codriverlabs.microvm.operator.core.model;

import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.Objects;

/**
 * Spec for MicroVMTemplate CR — reusable configuration template for MicroVMs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroVMTemplateSpec {

    private String imageRef;
    private String networkRef;
    private String ingressConnector;
    private String executionRoleArn;
    private Integer maxIdleDurationSeconds;
    private Integer suspendedDurationSeconds;
    private Boolean autoResumeEnabled;
    private Integer maximumDurationSeconds;
    private Map<String, String> tags;

    public String getImageRef() { return imageRef; }
    public void setImageRef(String imageRef) { this.imageRef = imageRef; }

    public String getNetworkRef() { return networkRef; }
    public void setNetworkRef(String networkRef) { this.networkRef = networkRef; }

    public String getIngressConnector() { return ingressConnector; }
    public void setIngressConnector(String ingressConnector) { this.ingressConnector = ingressConnector; }

    public String getExecutionRoleArn() { return executionRoleArn; }
    public void setExecutionRoleArn(String executionRoleArn) { this.executionRoleArn = executionRoleArn; }

    public Integer getMaxIdleDurationSeconds() { return maxIdleDurationSeconds; }
    public void setMaxIdleDurationSeconds(Integer v) { this.maxIdleDurationSeconds = v; }

    public Integer getSuspendedDurationSeconds() { return suspendedDurationSeconds; }
    public void setSuspendedDurationSeconds(Integer v) { this.suspendedDurationSeconds = v; }

    public Boolean getAutoResumeEnabled() { return autoResumeEnabled; }
    public void setAutoResumeEnabled(Boolean v) { this.autoResumeEnabled = v; }

    public Integer getMaximumDurationSeconds() { return maximumDurationSeconds; }
    public void setMaximumDurationSeconds(Integer v) { this.maximumDurationSeconds = v; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MicroVMTemplateSpec s)) return false;
        return Objects.equals(imageRef, s.imageRef) && Objects.equals(networkRef, s.networkRef);
    }

    @Override
    public int hashCode() { return Objects.hash(imageRef, networkRef); }

    @Override
    public String toString() { return "MicroVMTemplateSpec{imageRef=" + imageRef + "}"; }
}
