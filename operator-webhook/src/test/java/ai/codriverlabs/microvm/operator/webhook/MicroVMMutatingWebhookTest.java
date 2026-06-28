package ai.codriverlabs.microvm.operator.webhook;


import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import ai.codriverlabs.microvm.operator.webhook.mutation.MicroVMMutatingWebhook;
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
    void appliesDefaultMaxDurationWhenNull() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setImageRef("python-sandbox");
        spec.setMaximumDurationSeconds(null);
        spec.setMaxIdleDurationSeconds(2);

        MicroVMSpec mutated = webhook.applyDefaults(spec, "default");
        assertEquals(28800, mutated.getMaximumDurationSeconds());
    }

    @Test
    void appliesDefaultAutoResumeWhenNull() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setImageRef("python-sandbox");
        spec.setMaximumDurationSeconds(512);
        spec.setAutoResumeEnabled(null);

        MicroVMSpec mutated = webhook.applyDefaults(spec, "default");
        assertTrue(mutated.getAutoResumeEnabled());
    }

    @Test
    void doesNotOverrideExplicitMaxDuration() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setImageRef("python-sandbox");
        spec.setMaximumDurationSeconds(1024);
        spec.setMaxIdleDurationSeconds(4);

        MicroVMSpec mutated = webhook.applyDefaults(spec, "default");
        assertEquals(1024, mutated.getMaximumDurationSeconds());
    }

    @Test
    void doesNotOverrideExplicitAutoResume() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setImageRef("python-sandbox");
        spec.setMaximumDurationSeconds(512);
        spec.setAutoResumeEnabled(false);

        MicroVMSpec mutated = webhook.applyDefaults(spec, "default");
        assertFalse(mutated.getAutoResumeEnabled());
    }

    @Test
    void preservesAllOtherFields() {
        MicroVMSpec spec = new MicroVMSpec();
        spec.setImageRef("python-sandbox");
        spec.setMaximumDurationSeconds(768);
        spec.setMaxIdleDurationSeconds(3);
        spec.setSuspendedDurationSeconds(450);
        spec.setNetworkRef("my-network");
        spec.setRegion("eu-west-1");

        MicroVMSpec mutated = webhook.applyDefaults(spec, "default");
        assertEquals("python-sandbox", mutated.getImageRef());
        assertEquals(768, mutated.getMaximumDurationSeconds());
        assertEquals(3, mutated.getMaxIdleDurationSeconds());
        assertEquals(450, mutated.getSuspendedDurationSeconds());
        assertEquals("my-network", mutated.getNetworkRef());
        assertEquals("eu-west-1", mutated.getRegion());
    }
}
