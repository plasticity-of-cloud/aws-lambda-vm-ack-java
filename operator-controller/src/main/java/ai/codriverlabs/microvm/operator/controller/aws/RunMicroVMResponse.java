package ai.codriverlabs.microvm.operator.controller.aws;

/**
 * Response from RunMicrovm API.
 */
public record RunMicroVMResponse(
    String microvmId,
    String endpoint,
    String state
) {}
