package ai.codriverlabs.microvm.operator.core;

import ai.codriverlabs.microvm.operator.core.model.MicroVMSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;

/**
 * Feature: kube-microvm-operator, Property 10: Unknown Fields Are Ignored on Deserialization
 * Validates: Requirements 13.3
 */
class UnknownFieldsPropertyTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Property(tries = 100)
    void unknownFieldsPreservedOnRoundTrip(
            @ForAll("validMicroVMSpecJson") String originalJson,
            @ForAll("unknownFieldKey") String unknownKey,
            @ForAll("unknownFieldValue") String unknownValue) throws JsonProcessingException {

        // Parse the original JSON and inject an unknown field
        ObjectNode node = (ObjectNode) mapper.readTree(originalJson);
        node.put(unknownKey, unknownValue);

        String augmentedJson = mapper.writeValueAsString(node);

        // Deserialize should NOT fail
        MicroVMSpec deserialized = mapper.readValue(augmentedJson, MicroVMSpec.class);

        // Known fields should be intact
        assert deserialized.getImageRef() != null : "Runtime should be preserved";
        assert deserialized.getMaximumDurationSeconds() != null : "MemoryMB should be preserved";
        assert deserialized.getMaxIdleDurationSeconds() != null : "Vcpus should be preserved";

        // Unknown field should be preserved in additionalProperties
        assert deserialized.getAdditionalProperties().containsKey(unknownKey) :
            "Unknown field '" + unknownKey + "' should be preserved in additionalProperties";
        assert unknownValue.equals(deserialized.getAdditionalProperties().get(unknownKey)) :
            "Unknown field value should be preserved";

        // Re-serialize should include the unknown field
        String reserialized = mapper.writeValueAsString(deserialized);
        JsonNode reserializedNode = mapper.readTree(reserialized);
        assert reserializedNode.has(unknownKey) :
            "Unknown field should survive round-trip serialization";
    }

    @Provide
    Arbitrary<String> validMicroVMSpecJson() {
        return Arbitraries.of("python-sandbox", "ci-runner", "custom-image").map(imageRef -> {
            try {
                MicroVMSpec spec = new MicroVMSpec();
                spec.setImageRef(imageRef);
                spec.setMaximumDurationSeconds(512);
                spec.setMaxIdleDurationSeconds(2);
                spec.setSuspendedDurationSeconds(300);
                return mapper.writeValueAsString(spec);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Provide
    Arbitrary<String> unknownFieldKey() {
        // Generate keys that don't clash with known field names
        return Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(20)
                .map(s -> "x_unknown_" + s);
    }

    @Provide
    Arbitrary<String> unknownFieldValue() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
    }
}
