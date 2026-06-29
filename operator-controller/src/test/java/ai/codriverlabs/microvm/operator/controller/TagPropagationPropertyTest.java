package ai.codriverlabs.microvm.operator.controller;

import ai.codriverlabs.microvm.operator.controller.aws.RunMicroVMRequest;
import net.jqwik.api.*;

import java.util.Map;

/**
 * Feature: kube-microvm-operator, Property 11: Tag Propagation Completeness
 * Validates: Requirements 4.3, 6.4
 */
class TagPropagationPropertyTest {

    @Property(tries = 100)
    void allSpecTagsPropagatedToAwsRequest(@ForAll("tagMaps") Map<String, String> specTags) {
        RunMicroVMRequest request = new RunMicroVMRequest(
            "python-sandbox", "1.0", "arn:aws:iam::123456789012:role/exec",
            null, null, null,
            300, 600, true, 900,
            specTags, "us-east-1"
        );

        if (specTags != null) {
            for (Map.Entry<String, String> entry : specTags.entrySet()) {
                assert request.tags().containsKey(entry.getKey()) :
                    "Tag key '" + entry.getKey() + "' missing from request";
                assert request.tags().get(entry.getKey()).equals(entry.getValue()) :
                    "Tag value for key '" + entry.getKey() + "' was modified. Expected: " +
                    entry.getValue() + ", Got: " + request.tags().get(entry.getKey());
            }
            assert request.tags().size() == specTags.size() :
                "Request has " + request.tags().size() + " tags but spec had " + specTags.size();
        }
    }

    @Provide
    Arbitrary<Map<String, String>> tagMaps() {
        return Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
            Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(100)
        ).ofMaxSize(10);
    }
}
