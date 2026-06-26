package ai.codriverlabs.microvm.operator.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MicroVMImageSpec {

    private MicroVMImageSource source;
    private String baseImageArn;
    private Integer buildTimeoutSeconds;
    private Boolean autoActivate;

    public MicroVMImageSource getSource() { return source; }
    public void setSource(MicroVMImageSource source) { this.source = source; }

    public String getBaseImageArn() { return baseImageArn; }
    public void setBaseImageArn(String baseImageArn) { this.baseImageArn = baseImageArn; }

    public Integer getBuildTimeoutSeconds() { return buildTimeoutSeconds; }
    public void setBuildTimeoutSeconds(Integer buildTimeoutSeconds) { this.buildTimeoutSeconds = buildTimeoutSeconds; }

    public Boolean getAutoActivate() { return autoActivate; }
    public void setAutoActivate(Boolean autoActivate) { this.autoActivate = autoActivate; }
}
