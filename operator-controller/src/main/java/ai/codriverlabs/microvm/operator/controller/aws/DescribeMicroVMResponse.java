package ai.codriverlabs.microvm.operator.controller.aws;

/**
 * Response from GetMicrovm API.
 */
public record DescribeMicroVMResponse(
    String microvmId,
    String state,
    String endpoint,
    String imageIdentifier,
    String imageVersion
) {}
