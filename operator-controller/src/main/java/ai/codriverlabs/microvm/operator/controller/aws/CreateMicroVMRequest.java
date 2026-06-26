package ai.codriverlabs.microvm.operator.controller.aws;

import java.util.List;
import java.util.Map;

public record CreateMicroVMRequest(
    String runtime,
    int memoryMB,
    int vcpus,
    int timeoutSeconds,
    String vpcId,
    List<String> subnetIds,
    List<String> securityGroupIds,
    Map<String, String> tags,
    String region
) {}
