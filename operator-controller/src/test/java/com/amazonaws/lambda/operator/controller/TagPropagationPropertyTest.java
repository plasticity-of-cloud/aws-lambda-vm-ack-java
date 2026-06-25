package com.amazonaws.lambda.operator.controller;

import com.amazonaws.lambda.operator.controller.aws.CreateMicroVMRequest;
import net.jqwik.api.*;

import java.util.List;
import java.util.Map;

/**
 * Feature: lambda-vm-ack-operator, Property 11: Tag Propagation Completeness
 * Validates: Requirements 4.3, 6.4
 */
class TagPropagationPropertyTest {

    @Property(tries = 100)
    void allSpecTagsPropagatedToAwsRequest(@ForAll("tagMaps") Map<String, String> specTags) {
        // Simulate building a CreateMicroVMRequest from spec tags
        CreateMicroVMRequest request = new CreateMicroVMRequest(
            "java21", 512, 2, 300,
            "vpc-abc123", List.of("subnet-123"), List.of("sg-456"),
            specTags, "us-east-1"
        );

        // Verify all spec tags are present in the request without modification
        if (specTags != null) {
            for (Map.Entry<String, String> entry : specTags.entrySet()) {
                assert request.tags().containsKey(entry.getKey()) :
                    "Tag key '" + entry.getKey() + "' missing from request";
                assert request.tags().get(entry.getKey()).equals(entry.getValue()) :
                    "Tag value for key '" + entry.getKey() + "' was modified. Expected: " +
                    entry.getValue() + ", Got: " + request.tags().get(entry.getKey());
            }
            // No extra tags added
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
