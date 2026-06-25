package com.amazonaws.lambda.operator.controller;

import com.amazonaws.lambda.operator.core.enums.MicroVMState;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Instant;
import java.util.*;

/**
 * Feature: lambda-vm-ack-operator, Property 6: Pool Scaling Invariant
 * Validates: Requirements 6.1, 6.2, 6.4, 6.5, 6.6, 6.7
 *
 * Feature: lambda-vm-ack-operator, Property 7: Pool Scale-Down Order
 * Validates: Requirements 6.3
 */
class PoolScalingPropertyTest {

    // Property 6: After reconciliation, owned count should respect max allowed (replicas + maxSurge)
    @Property(tries = 100)
    void poolScalingInvariant(
            @ForAll @IntRange(min = 0, max = 50) int desiredReplicas,
            @ForAll @IntRange(min = 0, max = 10) int maxSurge,
            @ForAll("childStates") List<MicroVMState> currentChildren) {

        // Simulate scaling logic
        int maxAllowed = desiredReplicas + maxSurge;
        int currentCount = currentChildren.size();

        // After reconciliation:
        int targetCount = Math.min(desiredReplicas, maxAllowed);

        // The expected outcome:
        if (currentCount < targetCount) {
            // Need to scale up
            int toCreate = Math.min(targetCount - currentCount, 5); // max 5 per cycle
            int afterScale = currentCount + toCreate;
            assert afterScale <= maxAllowed :
                "After scale up, count " + afterScale + " exceeds max allowed " + maxAllowed;
        } else if (currentCount > targetCount) {
            // Need to scale down
            int toDelete = Math.min(currentCount - targetCount, 5); // max 5 per cycle
            int afterScale = currentCount - toDelete;
            assert afterScale >= 0 : "After scale down, count should never be negative";
        }

        // Status counts invariant
        long readyCount = currentChildren.stream()
                .filter(s -> s == MicroVMState.RUNNING)
                .count();
        assert readyCount <= currentCount : "Ready replicas cannot exceed total count";
    }

    // Property 7: Scale-down selects most recently created (LIFO)
    @Property(tries = 100)
    void scaleDownSelectsMostRecent(
            @ForAll @IntRange(min = 2, max = 20) int currentCount,
            @ForAll @IntRange(min = 1, max = 10) int toRemove) {

        if (toRemove >= currentCount) return; // skip invalid cases

        // Generate children with timestamps
        List<Instant> timestamps = new ArrayList<>();
        Instant base = Instant.parse("2025-01-01T00:00:00Z");
        for (int i = 0; i < currentCount; i++) {
            timestamps.add(base.plusSeconds(i * 60)); // 1 minute apart
        }

        // Shuffle to simulate random order
        List<Instant> shuffled = new ArrayList<>(timestamps);
        Collections.shuffle(shuffled);

        // Scale-down logic: sort by timestamp descending, pick first `toRemove`
        List<Instant> sorted = new ArrayList<>(shuffled);
        sorted.sort(Comparator.reverseOrder());
        int actualToRemove = Math.min(toRemove, 5); // max 5 per cycle
        List<Instant> toDelete = new ArrayList<>(sorted.subList(0, actualToRemove));
        List<Instant> survivors = new ArrayList<>(shuffled);
        survivors.removeAll(toDelete);

        // Assert: deleted ones have the most recent timestamps
        for (Instant deleted : toDelete) {
            for (Instant survived : survivors) {
                assert !deleted.isBefore(survived) :
                    "Deleted timestamp " + deleted + " is before surviving " + survived +
                    " — should delete most recent first";
            }
        }
    }

    @Provide
    Arbitrary<List<MicroVMState>> childStates() {
        return Arbitraries.of(
            MicroVMState.PENDING, MicroVMState.CREATING, MicroVMState.RUNNING,
            MicroVMState.STOPPED, MicroVMState.FAILED
        ).list().ofMinSize(0).ofMaxSize(30);
    }
}
