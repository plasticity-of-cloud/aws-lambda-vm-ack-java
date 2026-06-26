package ai.codriverlabs.microvm.operator.controller.aws;

public record CreateMicroVMResponse(
    String vmId,
    String ipAddress,
    String requestId
) {}
