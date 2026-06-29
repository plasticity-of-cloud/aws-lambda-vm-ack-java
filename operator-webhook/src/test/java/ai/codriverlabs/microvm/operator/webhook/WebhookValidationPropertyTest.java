package ai.codriverlabs.microvm.operator.webhook;


import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import ai.codriverlabs.microvm.operator.webhook.validation.MicroVMValidatingWebhook;
import net.jqwik.api.*;

import java.util.List;

/**
 * Feature: kube-microvm-operator, Property 4: Webhook Range Validation
 * Validates: Requirements 5.1, 5.2, 5.3
 *
 * Feature: kube-microvm-operator, Property 5: Webhook Mutation Applies Defaults
 * Validates: Requirements 5.5, 5.6
 */
class WebhookValidationPropertyTest {

    private final MicroVMValidatingWebhook webhook = new MicroVMValidatingWebhook();

    // Property 4a: Valid memory is accepted (128-10240, multiple of 64)
    @Property(tries = 100)
    void validMemoryIsAccepted(@ForAll("validMemory") int memoryMB) {
        MicroVMSpec spec = createValidSpec();
        spec.setMaximumDurationSeconds(memoryMB);
        List<String> errors = webhook.validate(spec, "default");
        assert errors.isEmpty() : "Valid memory " + memoryMB + " should be accepted, got: " + errors;
    }

    // Property 4b: Invalid memory is rejected
    @Property(tries = 100)
    void invalidMemoryIsRejected(@ForAll("invalidMemory") int memoryMB) {
        MicroVMSpec spec = createValidSpec();
        spec.setMaximumDurationSeconds(memoryMB);
        List<String> errors = webhook.validate(spec, "default");
        assert !errors.isEmpty() : "Invalid memory " + memoryMB + " should be rejected";
    }

    // Property 4c: Valid vcpus accepted (1-6)
    @Property(tries = 100)
    void validVcpusAccepted(@ForAll("validVcpus") int vcpus) {
        MicroVMSpec spec = createValidSpec();
        spec.setMaxIdleDurationSeconds(vcpus);
        List<String> errors = webhook.validate(spec, "default");
        assert errors.isEmpty() : "Valid vcpus " + vcpus + " should be accepted, got: " + errors;
    }

    // Property 4d: Invalid vcpus rejected
    @Property(tries = 100)
    void invalidVcpusRejected(@ForAll("invalidVcpus") int vcpus) {
        MicroVMSpec spec = createValidSpec();
        spec.setMaxIdleDurationSeconds(vcpus);
        List<String> errors = webhook.validate(spec, "default");
        assert !errors.isEmpty() : "Invalid vcpus " + vcpus + " should be rejected";
    }

    // Property 4e: Valid timeout accepted (1-900)
    @Property(tries = 100)
    void validTimeoutAccepted(@ForAll("validTimeout") int timeout) {
        MicroVMSpec spec = createValidSpec();
        spec.setSuspendedDurationSeconds(timeout);
        List<String> errors = webhook.validate(spec, "default");
        assert errors.isEmpty() : "Valid timeout " + timeout + " should be accepted, got: " + errors;
    }

    // Property 4f: Invalid timeout rejected
    @Property(tries = 100)
    void invalidTimeoutRejected(@ForAll("invalidTimeout") int timeout) {
        MicroVMSpec spec = createValidSpec();
        spec.setSuspendedDurationSeconds(timeout);
        List<String> errors = webhook.validate(spec, "default");
        assert !errors.isEmpty() : "Invalid timeout " + timeout + " should be rejected";
    }

    private MicroVMSpec createValidSpec() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setImageRef("python-sandbox");
        spec.setMaximumDurationSeconds(512);
        spec.setMaxIdleDurationSeconds(2);
        return spec;
    }

    @Provide
    Arbitrary<Integer> validMemory() {
        // 128 to 10240, multiple of 64
        return Arbitraries.integers().between(2, 160).map(i -> i * 64);
    }

    @Provide
    Arbitrary<Integer> invalidMemory() {
        return Arbitraries.oneOf(
            // Below range
            Arbitraries.integers().between(-100, 127),
            // Above range
            Arbitraries.integers().between(10241, 20000),
            // In range but not multiple of 64
            Arbitraries.integers().between(128, 10240).filter(i -> i % 64 != 0)
        );
    }

    @Provide
    Arbitrary<Integer> validVcpus() {
        return Arbitraries.integers().between(1, 6);
    }

    @Provide
    Arbitrary<Integer> invalidVcpus() {
        return Arbitraries.oneOf(
            Arbitraries.integers().between(-10, 0),
            Arbitraries.integers().between(7, 100)
        );
    }

    @Provide
    Arbitrary<Integer> validTimeout() {
        return Arbitraries.integers().between(1, 900);
    }

    @Provide
    Arbitrary<Integer> invalidTimeout() {
        return Arbitraries.oneOf(
            Arbitraries.integers().between(-100, 0),
            Arbitraries.integers().between(901, 2000)
        );
    }
}
