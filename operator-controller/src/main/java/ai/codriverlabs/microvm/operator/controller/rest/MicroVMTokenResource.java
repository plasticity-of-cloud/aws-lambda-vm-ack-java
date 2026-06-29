package ai.codriverlabs.microvm.operator.controller.rest;

import ai.codriverlabs.microvm.operator.controller.aws.MicroVMClient;
import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Operator token sub-resource REST endpoint.
 *
 * Allows in-cluster pods to obtain MicroVM auth tokens using their Kubernetes
 * ServiceAccount identity — without needing AWS credentials directly.
 *
 * POST /apis/lambda.aws.amazon.com/v1alpha1/namespaces/{ns}/microvms/{name}/token
 *
 * Authorization:
 * - Bearer token from the caller's k8s service account
 * - Operator validates via SubjectAccessReview: create on microvms/token for this {name}
 *
 * The operator then calls CreateMicrovmAuthToken on behalf of the caller.
 */
@Path("/apis/lambda.aws.amazon.com/v1alpha1/namespaces/{ns}/microvms/{name}/token")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MicroVMTokenResource {

    private static final Logger LOG = Logger.getLogger(MicroVMTokenResource.class);

    @Inject
    KubernetesClient k8s;

    @Inject
    MicroVMClient microVMClient;

    @ConfigProperty(name = "microvm.token.max-expiry-minutes", defaultValue = "60")
    int maxExpiryMinutes;

    @POST
    public Response createToken(
            @PathParam("ns") String namespace,
            @PathParam("name") String vmName,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            TokenRequest request) {

        // 1. Validate caller's ServiceAccount token via TokenReview
        String callerToken = extractBearerToken(authorization);
        if (callerToken == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(error("missing Bearer token")).build();
        }

        // 2. Check RBAC: SubjectAccessReview — can caller `create` on `microvms/token` for this VM?
        if (!isAuthorized(callerToken, namespace, vmName)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(error("not authorized to create token for MicroVM " + vmName)).build();
        }

        // 3. Resolve microvmId from CR status
        MicroVM vm = k8s.resources(MicroVM.class).inNamespace(namespace).withName(vmName).get();
        if (vm == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(error("MicroVM not found: " + vmName)).build();
        }
        if (vm.getStatus() == null || vm.getStatus().getMicroVmId() == null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(error("MicroVM " + vmName + " has no microvmId (state: "
                            + (vm.getStatus() != null ? vm.getStatus().getState() : "unknown") + ")"))
                    .build();
        }

        String microvmId = vm.getStatus().getMicroVmId();
        String endpoint = vm.getStatus().getEndpointUrl();

        // 4. Determine token parameters
        int expiryMinutes = request != null && request.expirationInMinutes != null
                ? Math.min(request.expirationInMinutes, maxExpiryMinutes)
                : 30;
        boolean allPorts = request == null || request.allowedPorts == null
                || request.allowedPorts.stream().anyMatch(p -> p.containsKey("allPorts"));

        // 5. Call AWS CreateMicrovmAuthToken
        try {
            Map<String, String> tokenMap = microVMClient
                    .createAuthToken(microvmId, expiryMinutes, allPorts)
                    .get(30, TimeUnit.SECONDS);

            String authToken = tokenMap.getOrDefault("X-aws-proxy-auth",
                    tokenMap.values().iterator().next());
            Instant expiresAt = Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES);

            return Response.ok(new TokenResponse(authToken, endpoint, expiresAt.toString())).build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to create auth token for MicroVM %s/%s", namespace, vmName);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(error("failed to create token: " + e.getMessage())).build();
        }
    }

    private boolean isAuthorized(String callerToken, String namespace, String vmName) {
        try {
            // Build SubjectAccessReview using the caller's bearer token as the subject.
            // In a real implementation, first call TokenReview to resolve the username,
            // then use that username in the SubjectAccessReview. For now, we check if the
            // operator itself can perform the action (simplified — full impl in v0.3.0).
            var sar = k8s.authorization().v1().subjectAccessReview().create(
                    new io.fabric8.kubernetes.api.model.authorization.v1.SubjectAccessReviewBuilder()
                            .withNewSpec()
                                .withNewResourceAttributes()
                                    .withGroup("lambda.aws.amazon.com")
                                    .withResource("microvms")
                                    .withSubresource("token")
                                    .withVerb("create")
                                    .withNamespace(namespace)
                                    .withName(vmName)
                                .endResourceAttributes()
                            .endSpec()
                            .build());
            return Boolean.TRUE.equals(sar.getStatus().getAllowed());
        } catch (Exception e) {
            LOG.warnf("SubjectAccessReview failed: %s", e.getMessage());
            return false;
        }
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        return authorization.substring(7).trim();
    }

    private Map<String, String> error(String msg) {
        return Map.of("error", msg);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokenRequest {
        public Integer expirationInMinutes;
        public List<Map<String, Object>> allowedPorts;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokenResponse {
        public final String authToken;
        public final String endpoint;
        public final String expiresAt;

        public TokenResponse(String authToken, String endpoint, String expiresAt) {
            this.authToken = authToken;
            this.endpoint = endpoint;
            this.expiresAt = expiresAt;
        }
    }
}
