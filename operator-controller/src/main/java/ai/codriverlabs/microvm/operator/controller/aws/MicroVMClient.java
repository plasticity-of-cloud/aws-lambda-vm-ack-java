package ai.codriverlabs.microvm.operator.controller.aws;

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
}
