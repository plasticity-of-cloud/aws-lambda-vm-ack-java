package ai.codriverlabs.microvm.operator.webhook;

import ai.codriverlabs.microvm.operator.core.enums.DesiredState;

import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import ai.codriverlabs.microvm.operator.webhook.mutation.MicroVMMutatingWebhook;
import net.jqwik.api.*;

import java.util.Objects;

/**
 * Feature: kube-microvm-operator, Property 5: Webhook Mutation Applies Defaults
 * Validates: Requirements 5.5, 5.6
 */
class WebhookMutationPropertyTest {

    private final MicroVMMutatingWebhook webhook = new MicroVMMutatingWebhook();

    @Property(tries = 100)
    void nullMaxDurationGetsDefault(@ForAll("specWithNullMaxDuration") MicroVMSpec spec) {
        MicroVMSpec mutated = webhook.applyDefaults(spec, "default");
        assert mutated.getMaximumDurationSeconds() == 28800 :
            "Null maximumDurationSeconds should default to 28800, got: " + mutated.getMaximumDurationSeconds();
    }

    @Property(tries = 100)
    void nullAutoResumeGetsDefault(@ForAll("specWithNullAutoResume") MicroVMSpec spec) {
        MicroVMSpec mutated = webhook.applyDefaults(spec, "default");
        assert mutated.getAutoResumeEnabled() == true :
            "Null autoResumeEnabled should default to true, got: " + mutated.getAutoResumeEnabled();
    }

    @Property(tries = 100)
    void explicitMaxDurationPreserved(@ForAll("specWithExplicitMaxDuration") MicroVMSpec spec) {
        Integer original = spec.getMaximumDurationSeconds();
        MicroVMSpec mutated = webhook.applyDefaults(spec, "default");
        assert mutated.getMaximumDurationSeconds().equals(original) :
            "Explicit maximumDurationSeconds " + original + " should be preserved, got: " + mutated.getMaximumDurationSeconds();
    }

    @Property(tries = 100)
    void otherFieldsUnchangedByMutation(@ForAll("anyValidSpec") MicroVMSpec spec) {
        String originalImageRef = spec.getImageRef();
        Integer originalVcpus = spec.getMaxIdleDurationSeconds();
        String originalNetRef = spec.getNetworkRef();
        String originalRegion = spec.getRegion();
        DesiredState originalDesired = spec.getDesiredState();

        webhook.applyDefaults(spec, "default");

        assert Objects.equals(spec.getImageRef(), originalImageRef) : "ImageRef changed";
        assert Objects.equals(spec.getMaxIdleDurationSeconds(), originalVcpus) : "MaxIdleDurationSeconds changed";
        assert Objects.equals(spec.getNetworkRef(), originalNetRef) : "NetworkRef changed";
        assert Objects.equals(spec.getRegion(), originalRegion) : "Region changed";
        assert spec.getDesiredState() == originalDesired : "DesiredState changed";
    }

    @Provide
    Arbitrary<MicroVMSpec> specWithNullMaxDuration() {
        return Arbitraries.of("python-sandbox", "ci-runner", "custom-image").map(img -> {
            MicroVMSpec spec = new MicroVMSpec();
            spec.setImageRef(img);
            spec.setMaximumDurationSeconds(null);
            spec.setMaxIdleDurationSeconds(2);
            return spec;
        });
    }

    @Provide
    Arbitrary<MicroVMSpec> specWithNullAutoResume() {
        return Arbitraries.of("python-sandbox", "ci-runner", "custom-image").map(img -> {
            MicroVMSpec spec = new MicroVMSpec();
            spec.setImageRef(img);
            spec.setMaximumDurationSeconds(512);
            spec.setAutoResumeEnabled(null);
            return spec;
        });
    }

    @Provide
    Arbitrary<MicroVMSpec> specWithExplicitMaxDuration() {
        return Combinators.combine(
            Arbitraries.of("python-sandbox", "ci-runner", "custom-image"),
            Arbitraries.integers().between(60, 86400)
        ).as((img, duration) -> {
            MicroVMSpec spec = new MicroVMSpec();
            spec.setImageRef(img);
            spec.setMaximumDurationSeconds(duration);
            spec.setMaxIdleDurationSeconds(2);
            return spec;
        });
    }

    @Provide
    Arbitrary<MicroVMSpec> anyValidSpec() {
        return Combinators.combine(
            Arbitraries.of("python-sandbox", "ci-runner", "custom-image"),
            Arbitraries.integers().between(60, 86400).injectNull(0.3),
            Arbitraries.integers().between(1, 6),
            Arbitraries.integers().between(1, 900).injectNull(0.3),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).injectNull(0.5),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(10).injectNull(0.7),
            Arbitraries.of(DesiredState.values()).injectNull(0.5)
        ).as((imageRef, maxDuration, idleDuration, suspended, netRef, region, desired) -> {
            MicroVMSpec spec = new MicroVMSpec();
            spec.setImageRef(imageRef);
            spec.setMaximumDurationSeconds(maxDuration);
            spec.setMaxIdleDurationSeconds(idleDuration);
            spec.setSuspendedDurationSeconds(suspended);
            spec.setNetworkRef(netRef);
            spec.setRegion(region);
            spec.setDesiredState(desired);
            return spec;
        });
    }
}
