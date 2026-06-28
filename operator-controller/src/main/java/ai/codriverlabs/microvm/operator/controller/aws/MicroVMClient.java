package ai.codriverlabs.microvm.operator.controller.aws;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client interface for AWS Lambda MicroVMs API (service: lambda-microvms, version 2025-09-09).
 */
public interface MicroVMClient {

    /** Calls RunMicrovm — creates and starts a new MicroVM from an image snapshot. */
    CompletableFuture<RunMicroVMResponse> runMicroVM(RunMicroVMRequest request);

    /** Calls GetMicrovm — describes current state of a MicroVM. */
    CompletableFuture<DescribeMicroVMResponse> getMicroVM(String microvmId);

    /** Calls SuspendMicrovm — suspends a running MicroVM (preserves state). */
    CompletableFuture<Void> suspendMicroVM(String microvmId);

    /** Calls ResumeMicrovm — resumes a suspended MicroVM. */
    CompletableFuture<Void> resumeMicroVM(String microvmId);

    /** Calls TerminateMicrovm — terminates a MicroVM and releases all resources. */
    CompletableFuture<Void> terminateMicroVM(String microvmId);

    /**
     * Calls CreateMicrovmAuthToken — generates a short-lived JWE token for connecting
     * to the MicroVM's HTTPS endpoint via the X-aws-proxy-auth header.
     *
     * @param microvmId          the MicroVM identifier
     * @param expirationMinutes  token lifetime in minutes
     * @param allPorts           if true, token allows access to all ports; otherwise port 8080 only
     */
    CompletableFuture<Map<String, String>> createAuthToken(
            String microvmId, int expirationMinutes, boolean allPorts);
}
