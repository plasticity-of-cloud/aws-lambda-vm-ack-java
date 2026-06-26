package ai.codriverlabs.microvm.operator.controller.aws;

public record DescribeMicroVMResponse(
    String vmId,
    String state,
    String ipAddress,
    String requestId
) {}
