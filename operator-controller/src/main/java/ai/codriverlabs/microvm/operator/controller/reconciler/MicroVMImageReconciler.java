package ai.codriverlabs.microvm.operator.controller.reconciler;

import ai.codriverlabs.microvm.aws.lambdamicrovms.model.MicrovmImageState;
import ai.codriverlabs.microvm.operator.controller.aws.MicroVMImageClient;
import ai.codriverlabs.microvm.operator.core.model.MicroVMImage;
import ai.codriverlabs.microvm.operator.core.model.MicroVMImageSpec;
import ai.codriverlabs.microvm.operator.core.model.MicroVMImageStatus;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@ControllerConfiguration(
    finalizerName = "lambda.aws.amazon.com/microvmimage-finalizer",
    retry = GenericRetry.class
)
public class MicroVMImageReconciler implements Reconciler<MicroVMImage>, Cleaner<MicroVMImage> {

    private static final Logger LOG = Logger.getLogger(MicroVMImageReconciler.class);
    private static final int TIMEOUT_S = 30;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(15);
    private static final Duration RESYNC = Duration.ofMinutes(5);

    private final MicroVMImageClient imageClient;

    @Inject
    public MicroVMImageReconciler(MicroVMImageClient imageClient) {
        this.imageClient = imageClient;
    }

    @Override
    public UpdateControl<MicroVMImage> reconcile(MicroVMImage resource, Context<MicroVMImage> ctx) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        MicroVMImageSpec spec = resource.getSpec();
        MicroVMImageStatus status = ensureStatus(resource);

        LOG.infof("Reconciling MicroVMImage %s/%s  imageArn=%s", namespace, name, status.getImageArn());

        try {
            // --- CREATE ---
            if (status.getImageArn() == null) {
                String s3Uri = "s3://" + spec.getSource().getS3Bucket() + "/" + spec.getSource().getS3Key();
                LOG.infof("Creating image %s from %s", name, s3Uri);
                var response = imageClient.createImage(
                        name, s3Uri, spec.getBaseImageArn(), spec.getBuildRoleArn())
                        .get(TIMEOUT_S, TimeUnit.SECONDS);

                status.setImageArn(response.imageArn());
                status.setImageState(response.stateAsString());
                status.setLatestVersion(response.imageVersion());
                status.setObservedGeneration(resource.getMetadata().getGeneration());
                LOG.infof("Image %s created: arn=%s state=%s", name, response.imageArn(), response.stateAsString());
                return UpdateControl.patchStatus(resource).rescheduleAfter(POLL_INTERVAL);
            }

            // --- UPDATE (spec changed) ---
            long gen = resource.getMetadata().getGeneration() != null ? resource.getMetadata().getGeneration() : 0L;
            long observed = status.getObservedGeneration() != null ? status.getObservedGeneration() : 0L;
            if (gen > observed && isBuildSettled(status.getImageState())) {
                String s3Uri = "s3://" + spec.getSource().getS3Bucket() + "/" + spec.getSource().getS3Key();
                LOG.infof("Updating image %s (generation %d -> %d)", name, observed, gen);
                var response = imageClient.updateImage(
                        status.getImageArn(), s3Uri, spec.getBaseImageArn(), spec.getBuildRoleArn())
                        .get(TIMEOUT_S, TimeUnit.SECONDS);
                status.setImageState(response.stateAsString());
                status.setObservedGeneration(gen);
                return UpdateControl.patchStatus(resource).rescheduleAfter(POLL_INTERVAL);
            }

            // --- POLL ---
            if (!isBuildSettled(status.getImageState())) {
                var response = imageClient.getImage(status.getImageArn()).get(TIMEOUT_S, TimeUnit.SECONDS);
                status.setImageState(response.stateAsString());
                LOG.infof("Image %s state: %s", name, response.stateAsString());
                return UpdateControl.patchStatus(resource).rescheduleAfter(POLL_INTERVAL);
            }

            // Settled — periodic resync
            return UpdateControl.patchStatus(resource).rescheduleAfter(RESYNC);

        } catch (Exception e) {
            LOG.errorf(e, "Error reconciling MicroVMImage %s/%s", namespace, name);
            return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(30));
        }
    }

    @Override
    public DeleteControl cleanup(MicroVMImage resource, Context<MicroVMImage> ctx) {
        MicroVMImageStatus status = resource.getStatus();
        if (status == null || status.getImageArn() == null) {
            return DeleteControl.defaultDelete();
        }
        try {
            LOG.infof("Deleting image %s  arn=%s", resource.getMetadata().getName(), status.getImageArn());
            imageClient.deleteImage(status.getImageArn()).get(TIMEOUT_S, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warnf("Error deleting image %s: %s — retrying", status.getImageArn(), e.getMessage());
            return DeleteControl.noFinalizerRemoval().rescheduleAfter(Duration.ofSeconds(15));
        }
        return DeleteControl.defaultDelete();
    }

    // States that mean a build is still in progress
    private boolean isBuildSettled(String state) {
        if (state == null) return false;
        return state.equals(MicrovmImageState.CREATED.toString())
                || state.equals(MicrovmImageState.UPDATED.toString())
                || state.equals(MicrovmImageState.CREATE_FAILED.toString())
                || state.equals(MicrovmImageState.UPDATE_FAILED.toString())
                || state.equals(MicrovmImageState.DELETE_FAILED.toString());
    }

    private MicroVMImageStatus ensureStatus(MicroVMImage resource) {
        if (resource.getStatus() == null) {
            resource.setStatus(new MicroVMImageStatus());
        }
        return resource.getStatus();
    }
}
