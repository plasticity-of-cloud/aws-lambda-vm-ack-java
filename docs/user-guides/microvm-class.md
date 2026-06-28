# MicroVMClass — Runtime Profiles

## Overview

`MicroVMClass` defines a named runtime profile (idle policy, networking, sizing) that platform
admins create once and developers reference by name via `spec.className` on a `MicroVM`.

This follows the Kubernetes `StorageClass` / `IngressClass` pattern:
- Admins define what classes are available
- Developers pick a class by name — no need to know the underlying ARNs or tuning values
- `spec.className` is **optional** — MicroVMs without a class use their own spec values

## Built-in Profiles (recommended starting point)

### `agentic-standard` — AI agents and interactive sessions

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVMClass
metadata:
  name: agentic-standard
  namespace: my-app
spec:
  description: "AI coding assistants, interactive dev environments — suspend when idle"
  maxIdleDurationSeconds: 300        # suspend after 5 min idle
  suspendedDurationSeconds: 7200     # keep suspended for 2 hr, then terminate
  autoResumeEnabled: true            # wake up automatically when traffic arrives
  maximumDurationSeconds: 28800      # 8 hr hard cap
  ingressNetworkConnectors:
    - arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:ALL_INGRESS
  egressNetworkConnectors:
    - arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:INTERNET_EGRESS
```

### `batch-job` — CI/CD jobs, security scans, one-shot tasks

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVMClass
metadata:
  name: batch-job
  namespace: my-app
spec:
  description: "Run-to-completion jobs — no suspend, hard 1 hr cap"
  autoResumeEnabled: false
  maximumDurationSeconds: 3600       # 1 hr max
  ingressNetworkConnectors:
    - arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:NO_INGRESS
  egressNetworkConnectors:
    - arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:INTERNET_EGRESS
```

### `vpc-agent` — Agents needing private VPC access

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVMClass
metadata:
  name: vpc-agent
  namespace: my-app
spec:
  description: "Agents that access RDS, ElastiCache, or internal APIs"
  maxIdleDurationSeconds: 300
  suspendedDurationSeconds: 3600
  autoResumeEnabled: true
  ingressNetworkConnectors:
    - arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:ALL_INGRESS
  egressNetworkConnectors:
    - arn:aws:lambda:us-east-1:123456789012:network-connector:my-vpc-connector  # customer-managed
```

## Using a MicroVMClass

Reference the class by `spec.className`:

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVM
metadata:
  name: agent-session-abc123
spec:
  imageRef: my-agent-image
  className: agentic-standard        # ← picks up idle policy + connectors
  executionRoleArn: arn:aws:iam::123456789012:role/AgentRole
  runHookPayload: '{"tenantId":"tenant-42","sessionId":"s-abc123"}'
```

The mutating webhook resolves the class and injects its values at admission time.
Fields explicitly set in the `MicroVM` spec always take precedence over class values.

## Override class values per-MicroVM

```yaml
spec:
  className: agentic-standard
  maxIdleDurationSeconds: 60   # override: suspend faster for this VM
  # all other fields from the class apply
```

## Field precedence

```
MicroVM spec field explicitly set  →  wins
MicroVMClass spec field            →  applied if MicroVM field is null
Global webhook default             →  applied if both are null
                                      (maximumDurationSeconds=28800, autoResumeEnabled=true)
```

## Without className (backward compatible)

`className` is always optional. Existing MicroVMs without it continue to work exactly as before.

```yaml
spec:
  imageRef: my-image
  # no className — spec values and global defaults used directly
  maxIdleDurationSeconds: 900
  autoResumeEnabled: true
```

## Listing available classes

```bash
kubectl get microvmclasses -n my-app
# NAME               MAXIDLE   AUTORESUME   DESCRIPTION
# agentic-standard   300       true         AI coding assistants...
# batch-job          <none>    false        Run-to-completion jobs...
```

## Cost implications

| Class | Active time | Idle cost | Best for |
|-------|-------------|-----------|----------|
| `agentic-standard` | Pay while active | ~$0 when suspended | < 13% utilization |
| `batch-job` | Pay for job duration | None (terminates) | Predictable jobs |

See [pricing comparison](../aws-microvms-official/01-overview.md) for detailed cost analysis.
