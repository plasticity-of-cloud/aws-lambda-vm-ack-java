package ai.codriverlabs.microvm.operator.webhook;

import ai.codriverlabs.microvm.operator.core.enums.Runtime;
import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import ai.codriverlabs.microvm.operator.webhook.mutation.MicroVMMutatingWebhook;
import ai.codriverlabs.microvm.operator.webhook.validation.MicroVMValidatingWebhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying the full admission pipeline:
 * mutation (apply defaults) -> validation (reject invalid).
 */
class WebhookIntegrationTest {

    private MicroVMValidatingWebhook validator;
    private MicroVMMutatingWebhook mutator;

    @BeforeEach
    void setUp() {
        validator = new MicroVMValidatingWebhook();
        mutator = new MicroVMMutatingWebhook();
    }

    @Test
    @DisplayName("Full pipeline: mutation applies defaults then validation passes")
    void fullPipelineMutationThenValidation() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setVcpus(2);
        // memoryMB and timeoutSeconds are null - should get defaults

        // Step 1: Mutate
        MicroVMSpec mutated = mutator.applyDefaults(spec);
        assertEquals(512, mutated.getMemoryMB());
        assertEquals(300, mutated.getTimeoutSeconds());

        // Step 2: Validate
        List<String> errors = validator.validate(mutated, "default");
        assertTrue(errors.isEmpty(), "After mutation, spec should pass validation: " + errors);
    }

    @Test
    @DisplayName("Invalid memoryMB rejected: below minimum")
    void invalidMemoryBelowMinimumRejected() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(64); // below 128 minimum
        spec.setVcpus(2);

        List<String> errors = validator.validate(spec, "default");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("memoryMB")));
    }

    @Test
    @DisplayName("Invalid memoryMB rejected: not multiple of 64")
    void invalidMemoryNotMultipleOf64Rejected() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(500); // not multiple of 64
        spec.setVcpus(2);

        List<String> errors = validator.validate(spec, "default");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("multiple of 64")));
    }

    @Test
    @DisplayName("Invalid vcpus rejected: above maximum")
    void invalidVcpusAboveMaxRejected() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(512);
        spec.setVcpus(8); // above 6 maximum

        List<String> errors = validator.validate(spec, "default");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("vcpus")));
    }

    @Test
    @DisplayName("Invalid runtime rejected: null")
    void invalidRuntimeNullRejected() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(null);
        spec.setMemoryMB(512);
        spec.setVcpus(2);

        List<String> errors = validator.validate(spec, "default");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("runtime")));
    }

    @Test
    @DisplayName("Mutation does not override explicitly set values")
    void mutationPreservesExplicitValues() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.PYTHON3_12);
        spec.setMemoryMB(1024);
        spec.setVcpus(4);
        spec.setTimeoutSeconds(600);
        spec.setNetworkRef("custom-network");

        MicroVMSpec mutated = mutator.applyDefaults(spec);

        assertEquals(Runtime.PYTHON3_12, mutated.getRuntime());
        assertEquals(1024, mutated.getMemoryMB());
        assertEquals(4, mutated.getVcpus());
        assertEquals(600, mutated.getTimeoutSeconds());
        assertEquals("custom-network", mutated.getNetworkRef());
    }

    @Test
    @DisplayName("Multiple validation errors aggregated in single response")
    void multipleValidationErrorsAggregated() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(null);       // invalid
        spec.setMemoryMB(50);        // invalid (below minimum)
        spec.setVcpus(10);           // invalid (above maximum)
        spec.setTimeoutSeconds(0);   // invalid (below minimum)

        List<String> errors = validator.validate(spec, "default");
        assertTrue(errors.size() >= 3, "Should have at least 3 errors, got " + errors.size() + ": " + errors);
    }

    @Test
    @DisplayName("Valid spec at boundary values passes")
    void boundaryValuesPass() {
        // Minimum valid values
        MicroVMSpec minSpec = new MicroVMSpec();
        minSpec.setRuntime(Runtime.CUSTOM);
        minSpec.setMemoryMB(128);
        minSpec.setVcpus(1);
        minSpec.setTimeoutSeconds(1);
        assertTrue(validator.validate(minSpec, "default").isEmpty());

        // Maximum valid values
        MicroVMSpec maxSpec = new MicroVMSpec();
        maxSpec.setRuntime(Runtime.NODEJS20);
        maxSpec.setMemoryMB(10240);
        maxSpec.setVcpus(6);
        maxSpec.setTimeoutSeconds(900);
        assertTrue(validator.validate(maxSpec, "default").isEmpty());
    }
}
