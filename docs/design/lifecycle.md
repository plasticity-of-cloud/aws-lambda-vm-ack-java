# Lifecycle & State Machine

## MicroVM States

The operator's state machine mirrors the AWS Lambda MicroVM lifecycle exactly:

```
                    ┌─────────┐
                    │ PENDING │
                    └────┬────┘
                         │ run-microvm succeeded
                         ↓
                    ┌─────────┐
              ┌─────│ RUNNING │←────────────────┐
              │     └────┬────┘                  │
              │          │                       │
    terminate │   idle / suspend-api      resume (auto or API)
              │          ↓                       │
              │   ┌────────────┐                 │
              │   │ SUSPENDING │                 │
              │   └─────┬──────┘                 │
              │         │ checkpoint complete    │
              │         ↓                        │
              │   ┌───────────┐                  │
              │   │ SUSPENDED │──────────────────┘
              │   └─────┬─────┘
              │         │ timeout or terminate-api
              ↓         ↓
        ┌─────────────┐
        │ TERMINATING │
        └──────┬──────┘
               │ resources released
               ↓
        ┌────────────┐
        │ TERMINATED │  (terminal)
        └────────────┘

        ┌────────┐
        │ FAILED │  (terminal — recoverable via recreate)
        └────────┘
```

## State Transition Table

| From | To | Trigger |
|------|----|---------|
| PENDING | RUNNING | Provisioning complete, /run hook succeeded |
| PENDING | FAILED | Provisioning error, image not found, network not ready |
| RUNNING | SUSPENDING | `idlePolicy.maxIdleDurationSeconds` exceeded OR `spec.desiredState` set to `Suspended` |
| SUSPENDING | SUSPENDED | Memory/disk checkpoint complete |
| SUSPENDED | RUNNING | Traffic arrives (autoResume) OR `spec.desiredState` set to `Running` |
| SUSPENDED | TERMINATING | `idlePolicy.suspendedDurationSeconds` exceeded OR `spec.desiredState` set to `Terminated` |
| RUNNING | TERMINATING | `spec.desiredState` set to `Terminated` OR `maximumDurationSeconds` exceeded |
| TERMINATING | TERMINATED | All resources released |
| FAILED | PENDING | Operator retries (with backoff) if error is retryable |

## Desired State Reconciliation

The operator reconciles `spec.desiredState` against `status.state`:

| desiredState | Current state | Action |
|-------------|---------------|--------|
| Running | Pending | Wait (provisioning in progress) |
| Running | Suspended | Call resume-microvm |
| Running | Running | No-op (aligned) |
| Suspended | Running | Call suspend-microvm |
| Suspended | Suspended | No-op (aligned) |
| Terminated | Running | Call terminate-microvm |
| Terminated | Suspended | Call terminate-microvm |
| Terminated | Terminated | No-op (remove finalizer, allow deletion) |

## Idle Policy

The `idlePolicy` on a MicroVM configures AWS-side suspend/resume behavior:

```yaml
idlePolicy:
  maxIdleDurationSeconds: 900     # Suspend after 15 min idle
  suspendedDurationSeconds: 3600  # Terminate after 1 hour suspended
  autoResumeEnabled: true         # Resume on inbound traffic
```

These values are passed to the `run-microvm` API as the `--idle-policy` parameter. The operator does NOT poll for idleness — AWS manages suspend/resume automatically. The operator observes state changes via `describe-microvm` during periodic re-sync.

## Re-sync Strategy

| State | Re-sync interval |
|-------|-----------------|
| PENDING | 5 seconds |
| RUNNING | 60 seconds |
| SUSPENDING | 5 seconds |
| SUSPENDED | 120 seconds |
| TERMINATING | 5 seconds |
| TERMINATED | No re-sync (terminal) |
| FAILED | 30 seconds (backoff) |

## Drift Detection

The operator compares `spec.desiredState` against the observed AWS state on every re-sync. Drift scenarios:

1. **AWS terminated unexpectedly** (spot reclaim, internal error) → Operator recreates if `desiredState != Terminated`
2. **User changed desiredState** → Operator executes the corresponding API call
3. **AWS suspended due to idle** → Operator updates `status.state` to reflect reality

## Finalizer Behavior

The finalizer `lambda.aws.amazon.com/microvm-finalizer` ensures:
1. `terminate-microvm` is called before the CR is removed
2. Operator waits for TERMINATED state confirmation
3. Only then removes the finalizer and allows Kubernetes garbage collection

If termination fails after 3 retries, the operator emits a warning event and removes the finalizer to avoid blocking deletion indefinitely.
