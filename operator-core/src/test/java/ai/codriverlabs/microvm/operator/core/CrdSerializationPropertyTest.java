package ai.codriverlabs.microvm.operator.core;

import ai.codriverlabs.microvm.operator.core.enums.DesiredState;
import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;

import java.util.*;

/**
 * Feature: kube-microvm-operator, Property 1: CRD Serialization Round Trip
 * Validates: Requirements 1.2, 1.3, 1.5, 1.6, 1.8, 1.10, 13.1, 13.2, 13.6
 */
class CrdSerializationPropertyTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Property(tries = 200)
    void microVMSpecRoundTrip(@ForAll("validMicroVMSpec") MicroVMSpec original) throws JsonProcessingException {
        String json = mapper.writeValueAsString(original);
        MicroVMSpec deserialized = mapper.readValue(json, MicroVMSpec.class);
        assert original.equals(deserialized) :
            "Round-trip failed.\nOriginal: " + original + "\nDeserialized: " + deserialized;
    }

    @Property(tries = 200)
    void microVMStatusRoundTrip(@ForAll("validMicroVMStatus") MicroVMStatus original) throws JsonProcessingException {
        String json = mapper.writeValueAsString(original);
        MicroVMStatus deserialized = mapper.readValue(json, MicroVMStatus.class);
        assert original.equals(deserialized) :
            "Round-trip failed.\nOriginal: " + original + "\nDeserialized: " + deserialized;
    }

    @Property(tries = 200)
    void microVMReplicaSetSpecRoundTrip(@ForAll("validMicroVMReplicaSetSpec") MicroVMReplicaSetSpec original) throws JsonProcessingException {
        String json = mapper.writeValueAsString(original);
        MicroVMReplicaSetSpec deserialized = mapper.readValue(json, MicroVMReplicaSetSpec.class);
        assert original.equals(deserialized) :
            "Round-trip failed.\nOriginal: " + original + "\nDeserialized: " + deserialized;
    }

    @Property(tries = 200)
    void microVMTemplateSpecRoundTrip(@ForAll("validMicroVMTemplateSpec") MicroVMTemplateSpec original) throws JsonProcessingException {
        String json = mapper.writeValueAsString(original);
        MicroVMTemplateSpec deserialized = mapper.readValue(json, MicroVMTemplateSpec.class);
        assert original.equals(deserialized) :
            "Round-trip failed.\nOriginal: " + original + "\nDeserialized: " + deserialized;
    }

    @Property(tries = 200)
    void microVMNetworkSpecRoundTrip(@ForAll("validMicroVMNetworkSpec") MicroVMNetworkSpec original) throws JsonProcessingException {
        String json = mapper.writeValueAsString(original);
        MicroVMNetworkSpec deserialized = mapper.readValue(json, MicroVMNetworkSpec.class);
        assert original.equals(deserialized) :
            "Round-trip failed.\nOriginal: " + original + "\nDeserialized: " + deserialized;
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<MicroVMSpec> validMicroVMSpec() {
        return Combinators.combine(
            Arbitraries.of("python-sandbox", "ci-runner", "custom-image"),
            Arbitraries.integers().between(128, 10240).filter(i -> i % 64 == 0),
            Arbitraries.integers().between(1, 6),
            Arbitraries.integers().between(1, 900).injectNull(0.3),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).injectNull(0.5),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).injectNull(0.5),
            Arbitraries.of(ai.codriverlabs.microvm.operator.core.enums.DesiredState.values()).injectNull(0.3),
            validTagMap()
        ).as((imageRef, mem, vcpus, timeout, netRef, tmplRef, desired, tags) -> {
            MicroVMSpec spec = new MicroVMSpec();
            spec.setImageRef(imageRef);
            spec.setMaximumDurationSeconds(mem);
            spec.setMaxIdleDurationSeconds(vcpus);
            spec.setSuspendedDurationSeconds(timeout);
            spec.setNetworkRef(netRef);
            spec.setTemplateRef(tmplRef);
            spec.setDesiredState(desired);
            spec.setTags(tags);
            return spec;
        });
    }

    @Provide
    Arbitrary<MicroVMStatus> validMicroVMStatus() {
        return Combinators.combine(
            Arbitraries.of(MicroVMState.values()),
            Arbitraries.strings().alpha().ofLength(10).injectNull(0.3),
            Arbitraries.strings().numeric().ofLength(12).injectNull(0.5),
            Arbitraries.longs().between(0L, 1000L).injectNull(0.3)
        ).as((state, vmId, ip, gen) -> {
            MicroVMStatus status = new MicroVMStatus();
            status.setState(state);
            status.setMicroVmId(vmId);
            status.setEndpointUrl(ip);
            status.setObservedGeneration(gen);
            return status;
        });
    }

    @Provide
    Arbitrary<MicroVMReplicaSetSpec> validMicroVMReplicaSetSpec() {
        return Combinators.combine(
            Arbitraries.integers().between(0, 100),
            Arbitraries.integers().between(0, 10).injectNull(0.3),
            Arbitraries.integers().between(1, 10).injectNull(0.3)
        ).as((replicas, minReady, maxSurge) -> {
            MicroVMReplicaSetSpec spec = new MicroVMReplicaSetSpec();
            spec.setReplicas(replicas);
            spec.setMinReady(minReady);
            spec.setMaxSurge(maxSurge);
            return spec;
        });
    }

    @Provide
    Arbitrary<MicroVMTemplateSpec> validMicroVMTemplateSpec() {
        return Combinators.combine(
            Arbitraries.of("python-sandbox", "ci-runner", "custom-image"),
            Arbitraries.integers().between(128, 10240).filter(i -> i % 64 == 0),
            Arbitraries.integers().between(1, 6),
            Arbitraries.integers().between(1, 900).injectNull(0.3),
            validStringMap(),
            validStringMap()
        ).as((imageRef, mem, vcpus, timeout, env, labels) -> {
            MicroVMTemplateSpec spec = new MicroVMTemplateSpec();
            spec.setImageRef(imageRef);
            spec.setMaximumDurationSeconds(mem);
            spec.setMaxIdleDurationSeconds(vcpus);
            spec.setSuspendedDurationSeconds(timeout);
            spec.setTags(env);
            // labels removed;
            return spec;
        });
    }

    @Provide
    Arbitrary<MicroVMNetworkSpec> validMicroVMNetworkSpec() {
        return Combinators.combine(
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(10)
                .map(s -> "subnet-" + s).list().ofMinSize(1).ofMaxSize(6),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(10)
                .map(s -> "sg-" + s).list().ofMinSize(1).ofMaxSize(5)
        ).as((subnets, sgs) -> {
            MicroVMNetworkSpec spec = new MicroVMNetworkSpec();
            spec.setSubnetIds(subnets);
            spec.setSecurityGroupIds(sgs);
            return spec;
        });
    }

    @Provide
    Arbitrary<Map<String, String>> validTagMap() {
        return Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(50)
        ).ofMaxSize(5).injectNull(0.2);
    }

    @Provide
    Arbitrary<Map<String, String>> validStringMap() {
        return Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
        ).ofMaxSize(5).injectNull(0.3);
    }
}
