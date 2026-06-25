package com.amazonaws.lambda.operator.webhook;

import com.amazonaws.lambda.operator.core.enums.Runtime;
import com.amazonaws.lambda.operator.core.model.MicroVMSpec;
import com.amazonaws.lambda.operator.webhook.validation.MicroVMValidatingWebhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MicroVMValidatingWebhookTest {

    private MicroVMValidatingWebhook webhook;

    @BeforeEach
    void setUp() {
        webhook = new MicroVMValidatingWebhook();
    }

    @Test
    void validSpecPassesValidation() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(512);
        spec.setVcpus(2);
        spec.setTimeoutSeconds(300);

        List<String> errors = webhook.validate(spec, "default");
        assertTrue(errors.isEmpty(), "Valid spec should have no errors: " + errors);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 64, 127, 10241, 10304, -1})
    void invalidMemoryRejected(int memoryMB) {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(memoryMB);
        spec.setVcpus(2);

        List<String> errors = webhook.validate(spec, "default");
        assertFalse(errors.isEmpty(), "Memory " + memoryMB + " should be rejected");
    }

    @ParameterizedTest
    @ValueSource(ints = {128, 256, 512, 1024, 10240})
    void validMemoryAccepted(int memoryMB) {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(memoryMB);
        spec.setVcpus(2);

        List<String> errors = webhook.validate(spec, "default");
        assertTrue(errors.isEmpty(), "Memory " + memoryMB + " should be accepted: " + errors);
    }

    @Test
    void memoryNotMultipleOf64Rejected() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(500); // not multiple of 64
        spec.setVcpus(2);

        List<String> errors = webhook.validate(spec, "default");
        assertFalse(errors.isEmpty(), "Memory 500 (not multiple of 64) should be rejected");
        assertTrue(errors.stream().anyMatch(e -> e.contains("multiple of 64")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 7, 8, 100})
    void invalidVcpusRejected(int vcpus) {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(512);
        spec.setVcpus(vcpus);

        List<String> errors = webhook.validate(spec, "default");
        assertFalse(errors.isEmpty(), "Vcpus " + vcpus + " should be rejected");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void validVcpusAccepted(int vcpus) {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(512);
        spec.setVcpus(vcpus);

        List<String> errors = webhook.validate(spec, "default");
        assertTrue(errors.isEmpty(), "Vcpus " + vcpus + " should be accepted: " + errors);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 901, 1000})
    void invalidTimeoutRejected(int timeout) {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(512);
        spec.setVcpus(2);
        spec.setTimeoutSeconds(timeout);

        List<String> errors = webhook.validate(spec, "default");
        assertFalse(errors.isEmpty(), "Timeout " + timeout + " should be rejected");
    }

    @Test
    void nullTimeoutIsAccepted() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(512);
        spec.setVcpus(2);
        spec.setTimeoutSeconds(null); // optional

        List<String> errors = webhook.validate(spec, "default");
        assertTrue(errors.isEmpty(), "Null timeout should be accepted: " + errors);
    }

    @Test
    void multipleErrorsAggregated() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(100);   // invalid
        spec.setVcpus(10);       // invalid
        spec.setTimeoutSeconds(0); // invalid

        List<String> errors = webhook.validate(spec, "default");
        assertTrue(errors.size() >= 3, "Should have at least 3 errors, got: " + errors);
    }
}
