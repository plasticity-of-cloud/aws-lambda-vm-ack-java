package ai.codriverlabs.microvm.operator.controller.aws;

import java.util.Map;

/**
 * Request to RunMicrovm API.
 */
public record RunMicroVMRequest(
    String imageIdentifier,
    String imageVersion,
    String executionRoleArn,
    String runHookPayload,
    String ingressNetworkConnector,
    String egressNetworkConnector,
    Integer maxIdleDurationSeconds,
    Integer suspendedDurationSeconds,
    Boolean autoResumeEnabled,
    Integer maximumDurationSeconds,
    Map<String, String> tags,
    String region
) {}
