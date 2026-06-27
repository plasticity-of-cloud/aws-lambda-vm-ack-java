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

    public List<MicroVMImageVersionInfo> getVersions() { return versions; }
    public void setVersions(List<MicroVMImageVersionInfo> versions) { this.versions = versions; }

    public Long getObservedGeneration() { return observedGeneration; }
    public void setObservedGeneration(Long observedGeneration) { this.observedGeneration = observedGeneration; }
}
