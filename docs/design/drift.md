# Drift Detection

## Overview

The operator continuously reconciles the desired state (Kubernetes CR) with the actual state (AWS). Drift detection identifies mismatches and triggers corrective actions.

## Drift Sources

| Source | Example | Detection method |
|--------|---------|-----------------|
| User changed CR | `desiredState: Running → Suspended` | Generation change (observedGeneration != generation) |
| AWS state changed | MicroVM terminated by max-duration | Periodic describe-microvm |
| External action | Console/CLI terminated a MicroVM | Periodic describe-microvm returns TERMINATED |
| Transient failure | Network timeout during suspend | State stuck in SUSPENDING beyond timeout |

## Detection Algorithm

On each reconcile cycle:

```
1. Fetch current AWS state: describe-microvm(status.microVmId)
2. Compare with spec.desiredState:
   - ALIGNED: AWS state matches desired → update status, schedule re-sync
   - DRIFT: AWS state diverged from desired → execute corrective action
   - NOT_FOUND: MicroVM doesn't exist in AWS → recreate if desired != Terminated
   - TRANSITIONAL: AWS in intermediate state moving toward desired → wait
   - ERROR: Unrecoverable mismatch → set Failed condition
```

## Decision Matrix

| Desired State | AWS Actual | Result | Action |
|--------------|-----------|--------|--------|
| Running | RUNNING | Aligned | No-op |
| Running | SUSPENDED | Drift | resume-microvm |
| Running | TERMINATED | Drift | Recreate (run-microvm) |
| Running | PENDING | Transitional | Wait |
| Running | SUSPENDING | Drift | Wait (cannot interrupt), then resume |
| Suspended | SUSPENDED | Aligned | No-op |
| Suspended | RUNNING | Drift | suspend-microvm |
| Suspended | TERMINATED | Error | Cannot suspend terminated VM |
| Terminated | TERMINATED | Aligned | Remove finalizer |
| Terminated | RUNNING | Drift | terminate-microvm |
| Terminated | SUSPENDED | Drift | terminate-microvm |
| * | NOT_FOUND | Drift/Error | Recreate if desired=Running, else mark Terminated |

## Self-Healing Scenarios

### 1. Unexpected Termination

```
Trigger: describe-microvm returns TERMINATED, but spec.desiredState = Running
Action:
  1. Clear status.microVmId
  2. Transition to PENDING
  3. Call run-microvm to create replacement
  4. Emit event: Warning UnexpectedTermination "MicroVM terminated unexpectedly, recreating"
```

### 2. Stuck in Transitional State

```
Trigger: State is SUSPENDING/TERMINATING for > 5 minutes
Action:
  1. Retry describe-microvm
  2. If still stuck after 10 minutes:
     - If SUSPENDING: mark as SUSPENDED (assume checkpoint completed)
     - If TERMINATING: mark as TERMINATED (assume resources released)
  3. Emit event: Warning StateTimeout "MicroVM stuck in <state>, forcing transition"
```

### 3. AWS API Errors

| Error type | Behavior |
|-----------|----------|
| Throttling (429) | Exponential backoff (10s, 20s, 40s, max 5min) |
| Not Found (404) | Trigger recreation if desired=Running |
| Auth failure (403) | Halt reconciliation, set AWSAuthError condition |
| Internal error (500) | Retry with backoff |
| Timeout | Retry immediately once, then backoff |

## Metrics

| Metric | Type | Labels |
|--------|------|--------|
| `microvm_drift_detected_total` | Counter | `type` (state_mismatch, not_found, unexpected_termination) |
| `microvm_drift_corrections_total` | Counter | `action` (resume, suspend, terminate, recreate) |
| `microvm_reconcile_duration_seconds` | Histogram | `outcome` (success, error, drift) |
| `microvm_aws_api_calls_total` | Counter | `operation`, `status_code` |
| `microvm_aws_api_duration_seconds` | Histogram | `operation` |

## Rate Limiting

The operator respects AWS API rate limits:
- Max 10 concurrent AWS API calls (configurable via `aws.microvm.thread-pool-size`)
- Per-resource: max 1 in-flight API call per MicroVM at a time
- Pool operations: max 5 mutations per reconcile cycle
