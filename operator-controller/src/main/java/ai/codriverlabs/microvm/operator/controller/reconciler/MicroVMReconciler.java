package ai.codriverlabs.microvm.operator.controller.reconciler;

import ai.codriverlabs.microvm.operator.controller.aws.*;
import ai.codriverlabs.microvm.operator.controller.metrics.OperatorMetrics;
import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.model.Condition;
import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import ai.codriverlabs.microvm.operator.core.model.MicroVMStatus;
import ai.codriverlabs.microvm.operator.core.state.MicroVMStateMachine;
import ai.codriverlabs.microvm.operator.core.state.StateTransitionResult;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ControllerConfiguration(
    finalizerName = "lambda.aws.amazon.com/microvm-finalizer",
    retry = GenericRetry.class
)
@io.quarkiverse.operatorsdk.annotations.AdditionalRBACRules({
    @io.quarkiverse.operatorsdk.annotations.RBACRule(
        apiGroups = "",
        resources = {"events"},
        verbs = {"create", "patch"}
    ),
    @io.quarkiverse.operatorsdk.annotations.RBACRule(
        apiGroups = "lambda.aws.amazon.com",
        resources = {"microvmtemplates", "microvmnetworks"},
        verbs = {"get", "list", "watch"}
    )
})
public class MicroVMReconciler implements Reconciler<MicroVM>, Cleaner<MicroVM> {

    private static final Logger LOG = Logger.getLogger(MicroVMReconciler.class);
    private static final Duration RESYNC_PERIOD = Duration.ofSeconds(60);
    private static final int AWS_TIMEOUT_SECONDS = 30;

    private final MicroVMClient microVMClient;
    private final MicroVMStateMachine stateMachine;
    private final DriftDetector driftDetector;
    private final OperatorMetrics metrics;
    private final KubernetesClient kubernetesClient;

    @Inject
    public MicroVMReconciler(MicroVMClient microVMClient,
                             MicroVMStateMachine stateMachine,
                             DriftDetector driftDetector,
                             OperatorMetrics metrics,
                             KubernetesClient kubernetesClient) {
        this.microVMClient = microVMClient;
        this.stateMachine = stateMachine;
        this.driftDetector = driftDetector;
        this.metrics = metrics;
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public UpdateControl<MicroVM> reconcile(MicroVM resource, Context<MicroVM> context) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        LOG.infof("Reconciling MicroVM %s/%s", namespace, name);

        long startTime = System.nanoTime();
        String outcome = "success";

        try {
            ensureStatusInitialized(resource);
            MicroVMStatus status = resource.getStatus();
            MicroVMState currentState = status.getState();

            // If in Pending state, begin creation
            if (currentState == MicroVMState.PENDING) {
                return handlePendingState(resource);
            }

            // If in Creating state, check if AWS resource is ready
            if (currentState == MicroVMState.PENDING) {
                return handleCreatingState(resource);
            }

            // Describe current state from AWS
            DescribeMicroVMResponse awsState = describeFromAws(status.getMicroVmId());
            if (awsState == null) {
                // Resource not found in AWS, recreate
                LOG.warnf("MicroVM %s/%s not found in AWS, transitioning to Creating", namespace, name);
                return transitionState(resource, MicroVMState.PENDING, "ResourceNotFound", "AWS resource not found, recreating");
            }

            // Detect drift between desired and actual state
            MicroVMState actualState = MicroVMState.fromValue(awsState.state());
            DesiredState desired = resource.getSpec().getDesiredState();
            DriftDetector.DriftResult driftResult = driftDetector.detectDrift(desired, actualState);

            return switch (driftResult) {
                case DriftDetector.DriftResult.NoOp noOp -> {
                    // Aligned - update status, sync tags, schedule re-sync
                    updateStatusFromAws(resource, awsState);
                    syncTags(resource, status.getMicroVmId());
                    yield UpdateControl.patchStatus(resource).rescheduleAfter(RESYNC_PERIOD);
                }
                case DriftDetector.DriftResult.ActionRequired action -> {
                    yield executeDriftAction(resource, action);
                }
                case DriftDetector.DriftResult.Error error -> {
                    outcome = "error";
                    yield handleReconcileError(resource, error.reason());
                }
            };
        } catch (AwsApiException e) {
            outcome = "error";
            return handleAwsException(resource, e);
        } catch (Exception e) {
            outcome = "error";
            LOG.errorf(e, "Unexpected error reconciling MicroVM %s/%s", namespace, name);
            return handleReconcileError(resource, "UnexpectedError: " + e.getMessage());
        } finally {
            long duration = System.nanoTime() - startTime;
            metrics.recordReconciliation(outcome, duration);
        }
    }

    @Override
    public DeleteControl cleanup(MicroVM resource, Context<MicroVM> context) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        LOG.infof("Cleaning up MicroVM %s/%s", namespace, name);

        MicroVMStatus status = resource.getStatus();
        if (status == null || status.getState() == null) {
            return DeleteControl.defaultDelete();
        }

        MicroVMState currentState = status.getState();

        // If already terminated, allow deletion
        if (currentState == MicroVMState.TERMINATED) {
            return DeleteControl.defaultDelete();
        }

        // Transition to Terminating
        StateTransitionResult result = stateMachine.transition(currentState, MicroVMState.TERMINATING);
        if (result instanceof StateTransitionResult.Valid) {
            status.setState(MicroVMState.TERMINATING);
            status.setLastTransitionTime(Instant.now());
            emitEvent(resource, "Terminating", "MicroVM deletion initiated");

            // Call AWS destroy
            try {
                String vmId = status.getMicroVmId();
                if (vmId != null) {
                    microVMClient.terminateMicroVM(vmId).get(AWS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
                status.setState(MicroVMState.TERMINATED);
                status.setLastTransitionTime(Instant.now());
                metrics.recordStateTransition(MicroVMState.TERMINATING, MicroVMState.TERMINATED);
                emitEvent(resource, "Terminated", "MicroVM successfully destroyed");
                return DeleteControl.defaultDelete();
            } catch (Exception e) {
                LOG.warnf(e, "Error destroying MicroVM %s/%s, retrying", namespace, name);
                return DeleteControl.noFinalizerRemoval().rescheduleAfter(Duration.ofSeconds(10));
            }
        }

        // If transition to Terminating is not valid (e.g., already Terminating)
        if (currentState == MicroVMState.TERMINATING) {
            try {
                String vmId = status.getMicroVmId();
                if (vmId != null) {
                    microVMClient.terminateMicroVM(vmId).get(AWS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
                status.setState(MicroVMState.TERMINATED);
                return DeleteControl.defaultDelete();
            } catch (Exception e) {
                return DeleteControl.noFinalizerRemoval().rescheduleAfter(Duration.ofSeconds(10));
            }
        }

        return DeleteControl.defaultDelete();
    }

    private void ensureStatusInitialized(MicroVM resource) {
        if (resource.getStatus() == null) {
            MicroVMStatus status = new MicroVMStatus();
            status.setState(MicroVMState.PENDING);
            status.setLastTransitionTime(Instant.now());
            resource.setStatus(status);
        }
    }

    private UpdateControl<MicroVM> handlePendingState(MicroVM resource) {
        MicroVMSpec spec = resource.getSpec();
        RunMicroVMRequest request = new RunMicroVMRequest(
            spec.getImageRef(),
            spec.getImageVersion(),
            spec.getExecutionRoleArn(),
            spec.getRunHookPayload(),
            spec.getIngressNetworkConnectors(),
            spec.getEgressNetworkConnectors(),
            spec.getMaxIdleDurationSeconds(),
            spec.getSuspendedDurationSeconds(),
            spec.getAutoResumeEnabled(),
            spec.getMaximumDurationSeconds(),
            spec.getTags(),
            spec.getRegion()
        );

        try {
            RunMicroVMResponse response = microVMClient.runMicroVM(request)
                .get(AWS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            resource.getStatus().setMicroVmId(response.microvmId());
            resource.getStatus().setEndpointUrl(response.endpoint());
            return transitionState(resource, MicroVMState.RUNNING, "Running", "MicroVM running, id=" + response.microvmId());
        } catch (Exception e) {
            return handleCreationError(resource, e);
        }
    }

    private UpdateControl<MicroVM> handleCreatingState(MicroVM resource) {
        String microvmId = resource.getStatus().getMicroVmId();
        if (microvmId == null) {
            return handlePendingState(resource);
        }

        try {
            DescribeMicroVMResponse response = microVMClient.getMicroVM(microvmId)
                .get(AWS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            MicroVMState awsState = MicroVMState.fromValue(response.state());
            if (awsState == MicroVMState.RUNNING) {
                resource.getStatus().setEndpointUrl(response.endpoint());
                return transitionState(resource, MicroVMState.RUNNING, "Running", "MicroVM is running");
            }

            // Still pending, requeue
            return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(5));
        } catch (AwsApiException e) {
            if (e.isNotFound()) {
                return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(5));
            }
            throw e;
        } catch (Exception e) {
            return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(10));
        }
    }

    private UpdateControl<MicroVM> executeDriftAction(MicroVM resource, DriftDetector.DriftResult.ActionRequired action) {
        String microvmId = resource.getStatus().getMicroVmId();
        try {
            CompletableFuture<Void> future = switch (action.action()) {
                case RECREATE -> CompletableFuture.completedFuture(null);
                case SUSPEND -> microVMClient.suspendMicroVM(microvmId);
                case RESUME -> microVMClient.resumeMicroVM(microvmId);
                case TERMINATE -> microVMClient.terminateMicroVM(microvmId);
                case NO_OP -> CompletableFuture.completedFuture(null);
            };

            if (action.action() == DriftDetector.DriftAction.RECREATE) {
                return transitionState(resource, MicroVMState.PENDING, "Recreating", "Drift correction: recreating MicroVM");
            }

            future.get(AWS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return transitionState(resource, action.targetState(), action.action().name(),
                "Drift correction: " + action.action().name().toLowerCase());
        } catch (Exception e) {
            return handleReconcileError(resource, "Failed to execute drift action " + action.action() + ": " + e.getMessage());
        }
    }

    private UpdateControl<MicroVM> transitionState(MicroVM resource, MicroVMState newState, String reason, String message) {
        MicroVMStatus status = resource.getStatus();
        MicroVMState oldState = status.getState();

        status.setState(newState);
        status.setLastTransitionTime(Instant.now());
        status.setObservedGeneration(resource.getMetadata().getGeneration());

        setReadyCondition(status, newState, reason, message);
        metrics.recordStateTransition(oldState, newState);
        emitEvent(resource, reason, message);

        return UpdateControl.patchStatus(resource).rescheduleAfter(RESYNC_PERIOD);
    }

    private UpdateControl<MicroVM> handleAwsException(MicroVM resource, AwsApiException e) {
        if (e.isRetryable()) {
            LOG.warnf("Retryable AWS error for %s/%s: %s", resource.getMetadata().getNamespace(),
                resource.getMetadata().getName(), e.getMessage());
            return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(10));
        }

        if (e.isNotFound()) {
            return transitionState(resource, MicroVMState.PENDING, "ResourceNotFound", "AWS resource not found, recreating");
        }

        if (e.isAuthFailure()) {
            setReadyCondition(resource.getStatus(), resource.getStatus().getState(), "AWSAuthError", e.getMessage());
            emitEvent(resource, "AWSAuthError", "AWS authentication failure: " + e.getMessage());
            return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(30));
        }

        // Non-retryable error
        return transitionState(resource, MicroVMState.FAILED, "AWSError", e.getMessage());
    }

    private UpdateControl<MicroVM> handleCreationError(MicroVM resource, Exception e) {
        if (e.getCause() instanceof AwsApiException awsEx && awsEx.isRetryable()) {
            return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(10));
        }
        return transitionState(resource, MicroVMState.FAILED, "CreationFailed", "Failed to create MicroVM: " + e.getMessage());
    }

    private UpdateControl<MicroVM> handleReconcileError(MicroVM resource, String reason) {
        setReadyCondition(resource.getStatus(), resource.getStatus().getState(), "Error", reason);
        return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(30));
    }

    private void setReadyCondition(MicroVMStatus status, MicroVMState state, String reason, String message) {
        String conditionStatus = (state == MicroVMState.RUNNING) ? "True" : "False";
        Condition ready = new Condition("Ready", conditionStatus, reason, message, Instant.now());

        status.getConditions().removeIf(c -> "Ready".equals(c.getType()));
        status.getConditions().add(ready);
    }

    private void updateStatusFromAws(MicroVM resource, DescribeMicroVMResponse awsState) {
        MicroVMStatus status = resource.getStatus();
        MicroVMState newState = MicroVMState.fromValue(awsState.state());

        if (status.getState() != newState) {
            metrics.recordStateTransition(status.getState(), newState);
            status.setState(newState);
            status.setLastTransitionTime(Instant.now());
        }

        status.setMicroVmId(awsState.microvmId());
        status.setEndpointUrl(awsState.endpoint());
        status.setObservedGeneration(resource.getMetadata().getGeneration());
    }

    private DescribeMicroVMResponse describeFromAws(String vmId) {
        if (vmId == null) return null;
        try {
            return microVMClient.getMicroVM(vmId).get(AWS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e.getCause() instanceof AwsApiException awsEx && awsEx.isNotFound()) {
                return null;
            }
            if (e.getCause() instanceof AwsApiException awsEx) {
                throw awsEx;
            }
            throw new RuntimeException("Failed to describe MicroVM: " + e.getMessage(), e);
        }
    }

    private void syncTags(MicroVM resource, String microvmId) {
        if (microvmId == null) return;
        Map<String, String> labels = resource.getMetadata().getLabels();
        try {
            // Fetch current AWS tags
            Map<String, String> awsTags = microVMClient.listTags(microvmId).get(AWS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Add/update labels that changed
            if (labels != null && !labels.isEmpty()) {
                microVMClient.tagResource(microvmId, labels).get(AWS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

            // Remove tags whose keys no longer exist in labels
            if (awsTags != null) {
                java.util.List<String> toRemove = awsTags.keySet().stream()
                        .filter(k -> labels == null || !labels.containsKey(k))
                        .collect(java.util.stream.Collectors.toList());
                if (!toRemove.isEmpty()) {
                    microVMClient.untagResource(microvmId, toRemove).get(AWS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to sync tags to MicroVM %s: %s", microvmId, e.getMessage());
        }
    }

    private void emitEvent(MicroVM resource, String reason, String message) {
        try {
            Event event = new EventBuilder()
                .withNewMetadata()
                    .withGenerateName(resource.getMetadata().getName() + "-")
                    .withNamespace(resource.getMetadata().getNamespace())
                .endMetadata()
                .withReason(reason)
                .withMessage(message)
                .withType(isErrorReason(reason) ? "Warning" : "Normal")
                .withInvolvedObject(new ObjectReferenceBuilder()
                    .withApiVersion(resource.getApiVersion())
                    .withKind(resource.getKind())
                    .withName(resource.getMetadata().getName())
                    .withNamespace(resource.getMetadata().getNamespace())
                    .withUid(resource.getMetadata().getUid())
                    .build())
                .withNewSource()
                    .withComponent("microvm-controller")
                .endSource()
                .build();

            kubernetesClient.v1().events().inNamespace(resource.getMetadata().getNamespace()).resource(event).create();
        } catch (Exception e) {
            LOG.warnf("Failed to emit event for %s/%s: %s",
                resource.getMetadata().getNamespace(), resource.getMetadata().getName(), e.getMessage());
        }
    }

    private boolean isErrorReason(String reason) {
        return reason != null && (reason.contains("Error") || reason.contains("Failed") || reason.contains("NotFound"));
    }
}
