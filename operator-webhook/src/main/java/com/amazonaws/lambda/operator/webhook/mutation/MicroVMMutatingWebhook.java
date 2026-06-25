package com.amazonaws.lambda.operator.webhook.mutation;

import com.amazonaws.lambda.operator.core.model.MicroVM;
import com.amazonaws.lambda.operator.core.model.MicroVMSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponse;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Mutating admission webhook for MicroVM resources.
 * Applies defaults for optional fields when not specified.
 * FailurePolicy: Ignore
 */
@Path("/mutate-microvm")
public class MicroVMMutatingWebhook {

    private static final Logger LOG = Logger.getLogger(MicroVMMutatingWebhook.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_MEMORY_MB = 512;

    private final ObjectMapper objectMapper;

    /**
     * No-arg constructor for unit testing.
     */
    public MicroVMMutatingWebhook() {
        this.objectMapper = null;
    }

    @Inject
    public MicroVMMutatingWebhook(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Applies default values to a MicroVMSpec for fields that are not set.
     * This method is extracted for unit testability.
     *
     * @param spec the MicroVMSpec to apply defaults to
     * @return the spec with defaults applied (same object, mutated in place)
     */
    public MicroVMSpec applyDefaults(MicroVMSpec spec) {
        if (spec.getTimeoutSeconds() == null) {
            spec.setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
        }
        if (spec.getMemoryMB() == null) {
            spec.setMemoryMB(DEFAULT_MEMORY_MB);
        }
        return spec;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public AdmissionReview mutate(AdmissionReview review) {
        AdmissionRequest request = review.getRequest();
        LOG.infof("Mutating MicroVM admission request: %s/%s, operation=%s",
            request.getNamespace(), request.getName(), request.getOperation());

        List<String> patches = new ArrayList<>();

        try {
            Object rawObject = request.getObject();
            MicroVM microVM = objectMapper.convertValue(rawObject, MicroVM.class);
            MicroVMSpec spec = microVM.getSpec();

            if (spec != null) {
                // Apply default timeoutSeconds if not set
                if (spec.getTimeoutSeconds() == null) {
                    patches.add(buildJsonPatchOp("add", "/spec/timeoutSeconds", DEFAULT_TIMEOUT_SECONDS));
                    LOG.debugf("Applying default timeoutSeconds=%d", DEFAULT_TIMEOUT_SECONDS);
                }

                // Apply default memoryMB if not set
                if (spec.getMemoryMB() == null) {
                    patches.add(buildJsonPatchOp("add", "/spec/memoryMB", DEFAULT_MEMORY_MB));
                    LOG.debugf("Applying default memoryMB=%d", DEFAULT_MEMORY_MB);
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error mutating MicroVM admission request");
            // On error, allow without mutation (failurePolicy: Ignore)
            return buildAllowedResponse(review);
        }

        return buildPatchResponse(review, patches);
    }

    private String buildJsonPatchOp(String op, String path, Object value) {
        if (value instanceof Number) {
            return String.format("{\"op\":\"%s\",\"path\":\"%s\",\"value\":%s}", op, path, value);
        }
        return String.format("{\"op\":\"%s\",\"path\":\"%s\",\"value\":\"%s\"}", op, path, value);
    }

    private AdmissionReview buildPatchResponse(AdmissionReview review, List<String> patches) {
        AdmissionResponse response = new AdmissionResponse();
        response.setUid(review.getRequest().getUid());
        response.setAllowed(true);

        if (!patches.isEmpty()) {
            String patchJson = "[" + String.join(",", patches) + "]";
            response.setPatchType("JSONPatch");
            response.setPatch(Base64.getEncoder().encodeToString(patchJson.getBytes()));
        }

        AdmissionReview responseReview = new AdmissionReview();
        responseReview.setResponse(response);
        return responseReview;
    }

    private AdmissionReview buildAllowedResponse(AdmissionReview review) {
        AdmissionResponse response = new AdmissionResponse();
        response.setUid(review.getRequest().getUid());
        response.setAllowed(true);

        AdmissionReview responseReview = new AdmissionReview();
        responseReview.setResponse(response);
        return responseReview;
    }
}
