# Pool Management

## Overview

`MicroVMPool` provides ReplicaSet-like semantics for MicroVMs — maintaining a desired count of identical instances, handling scale-up/down, rolling updates, and health-based eviction.

## Scaling

### Scale Up

When `status.currentReplicas < spec.replicas`:

1. Operator creates child `MicroVM` CRs from `spec.template`
2. Each child gets:
   - `metadata.generateName: <pool-name>-`
   - `metadata.labels: {"lambda.aws.amazon.com/pool-name": "<pool-name>"}`
   - `metadata.ownerReferences` pointing to the pool (cascade delete)
3. Maximum `maxSurge` additional MicroVMs beyond desired replicas (for rolling updates)
4. Throttled to 5 creations per reconcile cycle (prevent API burst)

### Scale Down

When `status.currentReplicas > spec.replicas`:

1. Select victims based on `spec.scaleDown.policy`:
   - `MostRecentFirst` (default) — delete newest first (preserves warm instances)
   - `OldestFirst` — delete oldest first (rotate for freshness)
   - `Random` — random selection
2. Set victim `spec.desiredState: Terminated`
3. Wait for TERMINATED state before removing from count
4. Throttled to 5 deletions per reconcile cycle
5. `stabilizationWindowSeconds` prevents rapid scale-down oscillation

### Scaling Constraints

```
0 ≤ replicas ≤ 100
0 ≤ maxSurge ≤ 10
total running ≤ replicas + maxSurge
```

## Rolling Updates

When `spec.template` changes (detected via `observedGeneration`):

1. Operator creates new MicroVMs from updated template (up to `maxSurge`)
2. As new MicroVMs reach RUNNING, operator terminates old ones
3. Maintains `minReady` MicroVMs available throughout the process
4. Update completes when all MicroVMs match the current template generation

```
Timeline:
  t0: template changes (e.g., new image version)
  t1: create new[0], new[1] (maxSurge=2)
  t2: new[0] → RUNNING, terminate old[0]
  t3: new[1] → RUNNING, terminate old[1]
  t4: create new[2], new[3]
  t5: ... until all replaced
```

## Health-Based Eviction

A MicroVM is considered unhealthy if:
- `status.state == FAILED` for more than 60 seconds
- `status.state == PENDING` for more than 300 seconds (stuck provisioning)
- Terminated unexpectedly (state changed to TERMINATED without desiredState=Terminated)

Unhealthy MicroVMs are deleted and the pool controller creates replacements automatically.

## Pool Status

```yaml
status:
  readyReplicas: 5        # state == RUNNING
  currentReplicas: 6      # total children (including surge)
  desiredReplicas: 5      # from spec.replicas
  suspendedReplicas: 0    # state == SUSPENDED
  updatedReplicas: 5      # matching current template generation
  observedGeneration: 3
  conditions:
    - type: Ready
      status: "True"
      reason: AllReplicasReady
    - type: Progressing
      status: "False"
      reason: RolloutComplete
```

## Pool ↔ MicroVM Relationship

```
MicroVMPool (owner)
  ├── MicroVM (child, ownerRef → pool)
  ├── MicroVM (child, ownerRef → pool)
  └── MicroVM (child, ownerRef → pool)
```

- Deleting the pool cascades to all children (Kubernetes GC)
- Children have label `lambda.aws.amazon.com/pool-name` for selection
- Children cannot be adopted by a different pool

## Suspend Pool

Setting all children to Suspended (cost optimization during off-hours):

```yaml
spec:
  desiredPoolState: Suspended  # Running (default) | Suspended
```

When `desiredPoolState: Suspended`, the operator sets `desiredState: Suspended` on all child MicroVMs. When switched back to `Running`, it resumes them. This is cheaper than terminating and recreating.
