package ai.codriverlabs.microvm.operator.controller.aws;

import java.util.List;
import java.util.Map;

/**
 * Request to RunMicrovm API.
 */
public record RunMicroVMRequest(
    String imageIdentifier,
    String imageVersion,
    String executionRoleArn,
    String runHookPayload,
    List<String> ingressNetworkConnectors,
    List<String> egressNetworkConnectors,
    Integer maxIdleDurationSeconds,
    Integer suspendedDurationSeconds,
    Boolean autoResumeEnabled,
    Integer maximumDurationSeconds,
    Map<String, String> tags,
    String region
) {}
