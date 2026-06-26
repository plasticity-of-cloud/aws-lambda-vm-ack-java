package ai.codriverlabs.microvm.operator.controller.aws;

import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over the AWS Lambda MicroVM API for testability.
 * All operations are async and return CompletableFutures.
 */
public interface MicroVMClient {

    CompletableFuture<CreateMicroVMResponse> createMicroVM(CreateMicroVMRequest request);

    CompletableFuture<DescribeMicroVMResponse> describeMicroVM(String vmId);

    CompletableFuture<Void> startMicroVM(String vmId);

    CompletableFuture<Void> stopMicroVM(String vmId);

    CompletableFuture<Void> pauseMicroVM(String vmId);

    CompletableFuture<Void> resumeMicroVM(String vmId);

    CompletableFuture<Void> destroyMicroVM(String vmId);
}
