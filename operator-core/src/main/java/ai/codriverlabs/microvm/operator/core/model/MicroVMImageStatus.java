package ai.codriverlabs.microvm.operator.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MicroVMImageStatus {

    /** Overall image state: CREATING, CREATED, CREATE_FAILED, UPDATING, UPDATED, UPDATE_FAILED, DELETING, DELETED */
    private String imageState;
    private String imageArn;
    private String latestVersion;   // String per API (e.g. "1.0")
    private String activeVersion;
    /** Version build state: PENDING, IN_PROGRESS, SUCCESSFUL, FAILED */
    private String latestVersionState;
    private String latestVersionStateReason;
    private List<MicroVMImageVersionInfo> versions = new ArrayList<>();
    private Long observedGeneration;

    public String getImageState() { return imageState; }
    public void setImageState(String imageState) { this.imageState = imageState; }

    public String getImageArn() { return imageArn; }
    public void setImageArn(String imageArn) { this.imageArn = imageArn; }

    public String getLatestVersion() { return latestVersion; }
    public void setLatestVersion(String latestVersion) { this.latestVersion = latestVersion; }

    public String getActiveVersion() { return activeVersion; }
    public void setActiveVersion(String activeVersion) { this.activeVersion = activeVersion; }

    public String getLatestVersionState() { return latestVersionState; }
    public void setLatestVersionState(String latestVersionState) { this.latestVersionState = latestVersionState; }

    public String getLatestVersionStateReason() { return latestVersionStateReason; }
    public void setLatestVersionStateReason(String r) { this.latestVersionStateReason = r; }

    public List<MicroVMImageVersionInfo> getVersions() { return versions; }
    public void setVersions(List<MicroVMImageVersionInfo> versions) { this.versions = versions; }

    public Long getObservedGeneration() { return observedGeneration; }
    public void setObservedGeneration(Long observedGeneration) { this.observedGeneration = observedGeneration; }
}
