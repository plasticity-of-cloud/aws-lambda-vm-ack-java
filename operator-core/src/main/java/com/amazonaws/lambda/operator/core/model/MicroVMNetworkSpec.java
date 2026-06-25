package com.amazonaws.lambda.operator.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroVMNetworkSpec {
    private String vpcId;
    private List<String> subnetIds;
    private List<String> securityGroupIds;

    public MicroVMNetworkSpec() {}

    public String getVpcId() { return vpcId; }
    public void setVpcId(String vpcId) { this.vpcId = vpcId; }
    public List<String> getSubnetIds() { return subnetIds; }
    public void setSubnetIds(List<String> subnetIds) { this.subnetIds = subnetIds; }
    public List<String> getSecurityGroupIds() { return securityGroupIds; }
    public void setSecurityGroupIds(List<String> securityGroupIds) { this.securityGroupIds = securityGroupIds; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MicroVMNetworkSpec s = (MicroVMNetworkSpec) o;
        return Objects.equals(vpcId, s.vpcId) && Objects.equals(subnetIds, s.subnetIds) &&
               Objects.equals(securityGroupIds, s.securityGroupIds);
    }

    @Override
    public int hashCode() { return Objects.hash(vpcId, subnetIds, securityGroupIds); }

    @Override
    public String toString() { return "MicroVMNetworkSpec{vpcId='" + vpcId + "', subnets=" + subnetIds + ", securityGroups=" + securityGroupIds + "}"; }
}
