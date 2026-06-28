package ai.codriverlabs.microvm.operator.tests.integration;

import ai.codriverlabs.microvm.operator.core.model.MicroVMClass;
import ai.codriverlabs.microvm.operator.core.model.MicroVMClassSpec;
import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import ai.codriverlabs.microvm.operator.webhook.mutation.MicroVMMutatingWebhook;
import ai.codriverlabs.microvm.operator.webhook.validation.MicroVMValidatingWebhook;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MicroVMClass — webhook mutation and validation.
 */
@EnableKubernetesMockClient(crud = true)
class MicroVMClassIT {

    KubernetesClient client;

    private MicroVMMutatingWebhook mutatingWebhook;
    private MicroVMValidatingWebhook validatingWebhook;

    @BeforeEach
    void setUp() {
        mutatingWebhook = new MicroVMMutatingWebhook(null, client);
        validatingWebhook = new MicroVMValidatingWebhook(client, null);
    }

    // ---- Mutation tests ----

    @Test
    @DisplayName("No className: global defaults applied, no class lookup")
    void noClassName_globalDefaultsApplied() {
        var spec = new MicroVMSpec();
        spec.setImageRef("my-image");
        // className not set

        mutatingWebhook.applyDefaults(spec, "default");

        assertEquals(28800, spec.getMaximumDurationSeconds());
        assertTrue(spec.getAutoResumeEnabled());
        assertNull(spec.getMaxIdleDurationSeconds()); // not set by global defaults
    }

    @Test
    @DisplayName("className set: class defaults merged into spec")
    void className_mergesClassDefaults() {
        createClass("agentic-1st", 300, 7200, true,
                List.of("arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:ALL_INGRESS"),
                List.of("arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:INTERNET_EGRESS"));

        var spec = new MicroVMSpec();
        spec.setImageRef("my-image");
        spec.setClassName("agentic-1st");

        mutatingWebhook.applyDefaults(spec, "default");

        assertEquals(300, spec.getMaxIdleDurationSeconds());
        assertEquals(7200, spec.getSuspendedDurationSeconds());
        assertTrue(spec.getAutoResumeEnabled());
        assertEquals(1, spec.getIngressNetworkConnectors().size());
        assertTrue(spec.getIngressNetworkConnectors().get(0).contains("ALL_INGRESS"));
        assertEquals(1, spec.getEgressNetworkConnectors().size());
    }

    @Test
    @DisplayName("spec fields take precedence over class defaults")
    void specFieldsTakePrecedenceOverClass() {
        createClass("agentic-1st", 300, 7200, true, null, null);

        var spec = new MicroVMSpec();
        spec.setImageRef("my-image");
        spec.setClassName("agentic-1st");
        spec.setMaxIdleDurationSeconds(600); // explicitly set — should NOT be overwritten

        mutatingWebhook.applyDefaults(spec, "default");

        assertEquals(600, spec.getMaxIdleDurationSeconds()); // preserved
        assertEquals(7200, spec.getSuspendedDurationSeconds()); // from class
    }

    @Test
    @DisplayName("className set but class not found: no error, global defaults applied")
    void className_notFound_fallsBackToGlobalDefaults() {
        var spec = new MicroVMSpec();
        spec.setImageRef("my-image");
        spec.setClassName("nonexistent-class");

        // Should not throw — FailurePolicy: Ignore
        assertDoesNotThrow(() -> mutatingWebhook.applyDefaults(spec, "default"));
        assertEquals(28800, spec.getMaximumDurationSeconds()); // global default applied
    }

    @Test
    @DisplayName("class has no ingressConnectors: spec connectors unchanged")
    void class_withoutConnectors_doesNotClearSpecConnectors() {
        createClass("minimal-class", 300, null, null, null, null);

        var spec = new MicroVMSpec();
        spec.setImageRef("my-image");
        spec.setClassName("minimal-class");
        spec.setIngressNetworkConnectors(List.of("arn:my-custom-ingress"));

        mutatingWebhook.applyDefaults(spec, "default");

        assertEquals(List.of("arn:my-custom-ingress"), spec.getIngressNetworkConnectors());
    }

    // ---- Validation tests ----

    @Test
    @DisplayName("Validation: no className → no error")
    void validation_noClassName_noError() {
        var spec = new MicroVMSpec();
        spec.setImageRef("my-image");

        var errors = new java.util.ArrayList<String>();
        validatingWebhook.validateClassName(spec, "default", errors);

        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Validation: className exists → no error")
    void validation_classNameExists_noError() {
        createClass("agentic-1st", 300, 7200, true, null, null);

        var spec = new MicroVMSpec();
        spec.setImageRef("my-image");
        spec.setClassName("agentic-1st");

        var errors = new java.util.ArrayList<String>();
        validatingWebhook.validateClassName(spec, "default", errors);

        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Validation: className not found → error")
    void validation_classNameNotFound_error() {
        var spec = new MicroVMSpec();
        spec.setImageRef("my-image");
        spec.setClassName("does-not-exist");

        var errors = new java.util.ArrayList<String>();
        validatingWebhook.validateClassName(spec, "default", errors);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("does-not-exist"));
    }

    // ---- helpers ----

    private void createClass(String name, Integer maxIdle, Integer suspendedDuration,
                              Boolean autoResume, List<String> ingress, List<String> egress) {
        var clazz = new MicroVMClass();
        clazz.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace("default").build());
        var spec = new MicroVMClassSpec();
        spec.setMaxIdleDurationSeconds(maxIdle);
        spec.setSuspendedDurationSeconds(suspendedDuration);
        spec.setAutoResumeEnabled(autoResume);
        spec.setIngressNetworkConnectors(ingress);
        spec.setEgressNetworkConnectors(egress);
        clazz.setSpec(spec);
        client.resource(clazz).create();
    }
}
