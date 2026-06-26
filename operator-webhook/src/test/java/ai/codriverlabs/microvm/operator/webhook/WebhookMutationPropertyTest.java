package ai.codriverlabs.microvm.operator.webhook;

import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.enums.Runtime;
import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import ai.codriverlabs.microvm.operator.webhook.mutation.MicroVMMutatingWebhook;
import net.jqwik.api.*;

/**
 * Feature: lambda-vm-ack-operator, Property 5: Webhook Mutation Applies Defaults
 * Validates: Requirements 5.5, 5.6
 */
class WebhookMutationPropertyTest {

    private final MicroVMMutatingWebhook webhook = new MicroVMMutatingWebhook();

    @Property(tries = 100)
    void nullTimeoutGetsDefault(@ForAll("specWithNullTimeout") MicroVMSpec spec) {
        MicroVMSpec mutated = webhook.applyDefaults(spec);
        assert mutated.getTimeoutSeconds() == 300 :
            "Null timeout should default to 300, got: " + mutated.getTimeoutSeconds();
    }

    @Property(tries = 100)
    void nullMemoryGetsDefault(@ForAll("specWithNullMemory") MicroVMSpec spec) {
        MicroVMSpec mutated = webhook.applyDefaults(spec);
        assert mutated.getMemoryMB() == 512 :
            "Null memory should default to 512, got: " + mutated.getMemoryMB();
    }

    @Property(tries = 100)
    void explicitTimeoutPreserved(@ForAll("specWithExplicitTimeout") MicroVMSpec spec) {
        Integer originalTimeout = spec.getTimeoutSeconds();
        MicroVMSpec mutated = webhook.applyDefaults(spec);
        assert mutated.getTimeoutSeconds().equals(originalTimeout) :
            "Explicit timeout " + originalTimeout + " should be preserved, got: " + mutated.getTimeoutSeconds();
    }

    @Property(tries = 100)
    void explicitMemoryPreserved(@ForAll("specWithExplicitMemory") MicroVMSpec spec) {
        Integer originalMemory = spec.getMemoryMB();
        MicroVMSpec mutated = webhook.applyDefaults(spec);
        assert mutated.getMemoryMB().equals(originalMemory) :
            "Explicit memory " + originalMemory + " should be preserved, got: " + mutated.getMemoryMB();
    }

    @Property(tries = 100)
    void otherFieldsUnchangedByMutation(@ForAll("anyValidSpec") MicroVMSpec spec) {
        Runtime originalRuntime = spec.getRuntime();
        Integer originalVcpus = spec.getVcpus();
        String originalNetRef = spec.getNetworkRef();
        String originalRegion = spec.getRegion();
        DesiredState originalDesired = spec.getDesiredState();

        webhook.applyDefaults(spec);

        assert spec.getRuntime() == originalRuntime : "Runtime changed";
        assert java.util.Objects.equals(spec.getVcpus(), originalVcpus) : "Vcpus changed";
        assert java.util.Objects.equals(spec.getNetworkRef(), originalNetRef) : "NetworkRef changed";
        assert java.util.Objects.equals(spec.getRegion(), originalRegion) : "Region changed";
        assert spec.getDesiredState() == originalDesired : "DesiredState changed";
    }

    @Provide
    Arbitrary<MicroVMSpec> specWithNullTimeout() {
        return Arbitraries.of(Runtime.values()).map(r -> {
            MicroVMSpec spec = new MicroVMSpec();
            spec.setRuntime(r);
            spec.setMemoryMB(512);
            spec.setVcpus(2);
            spec.setTimeoutSeconds(null);
            return spec;
        });
    }

    @Provide
    Arbitrary<MicroVMSpec> specWithNullMemory() {
        return Arbitraries.of(Runtime.values()).map(r -> {
            MicroVMSpec spec = new MicroVMSpec();
            spec.setRuntime(r);
            spec.setMemoryMB(null);
            spec.setVcpus(2);
            spec.setTimeoutSeconds(300);
            return spec;
        });
    }

    @Provide
    Arbitrary<MicroVMSpec> specWithExplicitTimeout() {
        return Combinators.combine(
            Arbitraries.of(Runtime.values()),
            Arbitraries.integers().between(1, 900)
        ).as((r, timeout) -> {
            MicroVMSpec spec = new MicroVMSpec();
            spec.setRuntime(r);
            spec.setMemoryMB(512);
            spec.setVcpus(2);
            spec.setTimeoutSeconds(timeout);
            return spec;
        });
    }

    @Provide
    Arbitrary<MicroVMSpec> specWithExplicitMemory() {
        return Combinators.combine(
            Arbitraries.of(Runtime.values()),
            Arbitraries.integers().between(2, 160).map(i -> i * 64)
        ).as((r, mem) -> {
            MicroVMSpec spec = new MicroVMSpec();
            spec.setRuntime(r);
            spec.setMemoryMB(mem);
            spec.setVcpus(2);
            return spec;
        });
    }

    @Provide
    Arbitrary<MicroVMSpec> anyValidSpec() {
        return Combinators.combine(
            Arbitraries.of(Runtime.values()),
            Arbitraries.integers().between(2, 160).map(i -> i * 64).injectNull(0.3),
            Arbitraries.integers().between(1, 6),
            Arbitraries.integers().between(1, 900).injectNull(0.3),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).injectNull(0.5),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(10).injectNull(0.7),
            Arbitraries.of(DesiredState.values()).injectNull(0.5)
        ).as((runtime, mem, vcpus, timeout, netRef, region, desired) -> {
            MicroVMSpec spec = new MicroVMSpec();
            spec.setRuntime(runtime);
            spec.setMemoryMB(mem);
            spec.setVcpus(vcpus);
            spec.setTimeoutSeconds(timeout);
            spec.setNetworkRef(netRef);
            spec.setRegion(region);
            spec.setDesiredState(desired);
            return spec;
        });
    }
}
