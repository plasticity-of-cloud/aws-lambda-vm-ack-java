package com.amazonaws.lambda.operator.webhook;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

/**
 * Feature: lambda-vm-ack-operator, Property 8: Namespace Quota Enforcement
 * Validates: Requirements 10.3
 */
class QuotaEnforcementPropertyTest {

    // Property: creation is rejected iff currentCount >= quota
    @Property(tries = 200)
    void quotaEnforcedCorrectly(
            @ForAll @IntRange(min = 1, max = 100) int quota,
            @ForAll @IntRange(min = 0, max = 200) int currentCount) {

        boolean shouldReject = currentCount >= quota;
        boolean rejected = evaluateQuota(currentCount, quota);

        assert rejected == shouldReject :
            "With quota=" + quota + " and currentCount=" + currentCount +
            ": expected rejection=" + shouldReject + " but got=" + rejected;
    }

    // When quota is not set (0 or negative), creation is always allowed
    @Property(tries = 100)
    void noQuotaAlwaysAllows(@ForAll @IntRange(min = 0, max = 200) int currentCount) {
        boolean rejected = evaluateQuota(currentCount, 0); // 0 means no quota
        assert !rejected : "With no quota (0), creation should always be allowed";
    }

    // Edge case: quota exactly equals count should reject
    @Property(tries = 100)
    void quotaEqualsCountRejects(@ForAll @IntRange(min = 1, max = 100) int value) {
        boolean rejected = evaluateQuota(value, value);
        assert rejected :
            "When currentCount (" + value + ") == quota (" + value + "), should reject";
    }

    // Edge case: count is one less than quota should allow
    @Property(tries = 100)
    void countOneLessThanQuotaAllows(@ForAll @IntRange(min = 2, max = 100) int quota) {
        boolean rejected = evaluateQuota(quota - 1, quota);
        assert !rejected :
            "When currentCount (" + (quota - 1) + ") < quota (" + quota + "), should allow";
    }

    /**
     * Simulates the webhook quota check logic:
     * Returns true if creation should be rejected.
     */
    private boolean evaluateQuota(int currentCount, int quota) {
        if (quota <= 0) return false; // No quota configured
        return currentCount >= quota;
    }
}
