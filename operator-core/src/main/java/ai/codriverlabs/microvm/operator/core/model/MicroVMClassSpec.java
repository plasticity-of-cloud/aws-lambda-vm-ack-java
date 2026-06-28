package ai.codriverlabs.microvm.operator.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Runtime profile spec for MicroVMClass.
 * All fields are optional — only set fields are merged into the MicroVM spec.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MicroVMClassSpec {

    // --- Idle policy ---
    /** Seconds without traffic before Lambda auto-suspends the MicroVM. Max 28800 (8h). */
    private Integer maxIdleDurationSeconds;
    /** Seconds in SUSPENDED state before Lambda auto-terminates. */
    private Integer suspendedDurationSeconds;
    /** When true, a suspended MicroVM auto-resumes when traffic arrives. */
    private Boolean autoResumeEnabled;

    // --- Lifetime ---
    /** Hard cap on total MicroVM lifetime (running + suspended). Max 28800 (8h). */
    private Integer maximumDurationSeconds;

    // --- Networking ---
    /** Inbound connector ARNs (e.g. ALL_INGRESS). */
    private List<String> ingressNetworkConnectors;
    /** Outbound connector ARNs (e.g. INTERNET_EGRESS or VPC connector). */
    private List<String> egressNetworkConnectors;

    // --- Description ---
    private String description;

    public Integer getMaxIdleDurationSeconds() { return maxIdleDurationSeconds; }
    public void setMaxIdleDurationSeconds(Integer v) { this.maxIdleDurationSeconds = v; }

    public Integer getSuspendedDurationSeconds() { return suspendedDurationSeconds; }
    public void setSuspendedDurationSeconds(Integer v) { this.suspendedDurationSeconds = v; }

    public Boolean getAutoResumeEnabled() { return autoResumeEnabled; }
    public void setAutoResumeEnabled(Boolean v) { this.autoResumeEnabled = v; }

    public Integer getMaximumDurationSeconds() { return maximumDurationSeconds; }
    public void setMaximumDurationSeconds(Integer v) { this.maximumDurationSeconds = v; }

    public List<String> getIngressNetworkConnectors() { return ingressNetworkConnectors; }
    public void setIngressNetworkConnectors(List<String> v) { this.ingressNetworkConnectors = v; }

    public List<String> getEgressNetworkConnectors() { return egressNetworkConnectors; }
    public void setEgressNetworkConnectors(List<String> v) { this.egressNetworkConnectors = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
}
