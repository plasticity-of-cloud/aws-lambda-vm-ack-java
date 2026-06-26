package ai.codriverlabs.microvm.operator.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MicroVMImageStatus {

    private String imageArn;
    private Integer latestVersion;
    private Integer activeVersion;
    private List<MicroVMImageVersionInfo> versions;

    public String getImageArn() { return imageArn; }
    public void setImageArn(String imageArn) { this.imageArn = imageArn; }

    public Integer getLatestVersion() { return latestVersion; }
    public void setLatestVersion(Integer latestVersion) { this.latestVersion = latestVersion; }

    public Integer getActiveVersion() { return activeVersion; }
    public void setActiveVersion(Integer activeVersion) { this.activeVersion = activeVersion; }

    public List<MicroVMImageVersionInfo> getVersions() { return versions; }
    public void setVersions(List<MicroVMImageVersionInfo> versions) { this.versions = versions; }
}
