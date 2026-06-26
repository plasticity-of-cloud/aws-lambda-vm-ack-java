package ai.codriverlabs.microvm.operator.controller.aws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Default implementation of MicroVMClient wrapping the AWS Lambda MicroVMs API (lambda-microvms 2025-09-09).
 * Placeholder until the official Java SDK artifact is released.
 */
@ApplicationScoped
public class DefaultMicroVMClient implements MicroVMClient {

    private static final Logger LOG = Logger.getLogger(DefaultMicroVMClient.class);

    private final ExecutorService executor;
    private final String defaultRegion;

    @Inject
    public DefaultMicroVMClient(
            @ConfigProperty(name = "aws.region", defaultValue = "us-east-1") String defaultRegion,
            @ConfigProperty(name = "aws.microvm.thread-pool-size", defaultValue = "10") int threadPoolSize) {
        this.defaultRegion = defaultRegion;
        this.executor = Executors.newFixedThreadPool(threadPoolSize);
    }

    @Override
    public CompletableFuture<RunMicroVMResponse> runMicroVM(RunMicroVMRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            LOG.infof("RunMicrovm: image=%s, region=%s", request.imageIdentifier(),
                    request.region() != null ? request.region() : defaultRegion);
            // TODO: Replace with LambdaMicrovmsClient.runMicrovm() when Java SDK available
            throw new UnsupportedOperationException("AWS Lambda MicroVMs Java SDK not yet available");
        }, executor);
    }

    @Override
    public CompletableFuture<DescribeMicroVMResponse> getMicroVM(String microvmId) {
        return CompletableFuture.supplyAsync(() -> {
            LOG.debugf("GetMicrovm: id=%s", microvmId);
            throw new UnsupportedOperationException("AWS Lambda MicroVMs Java SDK not yet available");
        }, executor);
    }

    @Override
    public CompletableFuture<Void> suspendMicroVM(String microvmId) {
        return CompletableFuture.runAsync(() -> {
            LOG.infof("SuspendMicrovm: id=%s", microvmId);
            throw new UnsupportedOperationException("AWS Lambda MicroVMs Java SDK not yet available");
        }, executor);
    }

    @Override
    public CompletableFuture<Void> resumeMicroVM(String microvmId) {
        return CompletableFuture.runAsync(() -> {
            LOG.infof("ResumeMicrovm: id=%s", microvmId);
            throw new UnsupportedOperationException("AWS Lambda MicroVMs Java SDK not yet available");
        }, executor);
    }

    @Override
    public CompletableFuture<Void> terminateMicroVM(String microvmId) {
        return CompletableFuture.runAsync(() -> {
            LOG.infof("TerminateMicrovm: id=%s", microvmId);
            throw new UnsupportedOperationException("AWS Lambda MicroVMs Java SDK not yet available");
        }, executor);
    }
}
