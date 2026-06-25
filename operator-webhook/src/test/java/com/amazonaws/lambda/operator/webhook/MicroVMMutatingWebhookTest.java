package com.amazonaws.lambda.operator.webhook;

import com.amazonaws.lambda.operator.core.enums.Runtime;
import com.amazonaws.lambda.operator.core.model.MicroVMSpec;
import com.amazonaws.lambda.operator.webhook.mutation.MicroVMMutatingWebhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MicroVMMutatingWebhookTest {

    private MicroVMMutatingWebhook webhook;

    @BeforeEach
    void setUp() {
        webhook = new MicroVMMutatingWebhook();
    }

    @Test
    void appliesDefaultTimeoutWhenNull() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(512);
        spec.setVcpus(2);
        spec.setTimeoutSeconds(null);

        MicroVMSpec mutated = webhook.applyDefaults(spec);
        assertEquals(300, mutated.getTimeoutSeconds());
    }

    @Test
    void appliesDefaultMemoryWhenNull() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(null);
        spec.setVcpus(2);

        MicroVMSpec mutated = webhook.applyDefaults(spec);
        assertEquals(512, mutated.getMemoryMB());
    }

    @Test
    void doesNotOverrideExplicitTimeout() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(1024);
        spec.setVcpus(4);
        spec.setTimeoutSeconds(600);

        MicroVMSpec mutated = webhook.applyDefaults(spec);
        assertEquals(600, mutated.getTimeoutSeconds());
    }

    @Test
    void doesNotOverrideExplicitMemory() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.JAVA21);
        spec.setMemoryMB(1024);
        spec.setVcpus(2);

        MicroVMSpec mutated = webhook.applyDefaults(spec);
        assertEquals(1024, mutated.getMemoryMB());
    }

    @Test
    void preservesAllOtherFields() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setRuntime(Runtime.PYTHON3_12);
        spec.setMemoryMB(768);
        spec.setVcpus(3);
        spec.setTimeoutSeconds(450);
        spec.setNetworkRef("my-network");
        spec.setRegion("eu-west-1");

        MicroVMSpec mutated = webhook.applyDefaults(spec);
        assertEquals(Runtime.PYTHON3_12, mutated.getRuntime());
        assertEquals(768, mutated.getMemoryMB());
        assertEquals(3, mutated.getVcpus());
        assertEquals(450, mutated.getTimeoutSeconds());
        assertEquals("my-network", mutated.getNetworkRef());
        assertEquals("eu-west-1", mutated.getRegion());
    }
}
