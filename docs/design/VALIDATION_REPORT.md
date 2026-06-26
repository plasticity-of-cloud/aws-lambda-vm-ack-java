# Design Validation Against AWS Official Documentation

**Source**: https://docs.aws.amazon.com/lambda/latest/dg/lambda-microvms-guide.html  
**Validated**: 2026-06-26

## Critical Corrections

### 1. Memory size is set at IMAGE level, not per-MicroVM

**Official**: "You set the baseline via the `memory` parameter when creating your MicroVM image."

**Impact**: `memorySizeMB` moves from `MicroVM.spec` to `MicroVMImage.spec`. A MicroVM inherits its size from the image.

### 2. No STOPPED/PAUSED states — only SUSPENDED

**Official states**: PENDING → RUNNING → SUSPENDING → SUSPENDED → RUNNING → TERMINATING → TERMINATED

**Impact**: Remove all references to `STOPPED`, `STOPPING`, `PAUSED`, `STARTING` from state machine, enums, and drift detector.

### 3. DesiredState values

**Official**: Running, Suspended, Terminated (no Stopped, no Paused)

### 4. API operations differ from our naming

| Our design | Official API |
|-----------|-------------|
| createMicroVM | `run-microvm` |
| startMicroVM | (not a thing — use `resume-microvm`) |
| stopMicroVM | (not a thing — use `suspend-microvm`) |
| pauseMicroVM | (not a thing) |
| resumeMicroVM | `resume-microvm` |
| destroyMicroVM | `terminate-microvm` |
| describeMicroVM | `describe-microvm` |

### 5. Missing fields in MicroVM spec

From `run-microvm` parameters:
- `executionRoleArn` — IAM role for the MicroVM runtime (access to other AWS services)
- `runHookPayload` — per-instance string payload (max 16KB) delivered to /run hook
- `logging` — CloudWatch log group/stream configuration
- `ingressNetworkConnectors` — ARN(s) for inbound connectivity (default: ALL_INGRESS)

### 6. Missing fields in MicroVMImage spec

- `memorySizeMB` — baseline compute size (determines vCPU allocation)
- `buildRoleArn` — IAM role Lambda assumes during image build (S3 access, ECR access)
- `environmentVariables` — set at build time, shared across all MicroVMs from this image
- `additionalOsCapabilities` — elevated Linux capabilities (e.g., ["ALL"])
- `description` — human-readable description
- `hookPort` — port for lifecycle hook HTTP calls
- `readyTimeoutInSeconds` — timeout for /ready hook during build
- `validateTimeoutInSeconds` — timeout for /validate hook

### 7. Webhook validation range incorrect

**Our design**: `maxIdleDurationSeconds` max = 3600  
**Official**: `maxIdleDurationSeconds` max = 28800 (8 hours)

### 8. Image version states differ

**Official**:
- Image state: CREATING → CREATED → UPDATING → UPDATED (or *_FAILED variants)
- Version state: PENDING → IN_PROGRESS → SUCCESSFUL (or FAILED)
- Version activation: ACTIVE / INACTIVE (user-controlled, independent of build state)

**Our design** conflated these into one. Need three separate status fields.

### 9. Image memory → vCPU mapping (official table)

| Memory | vCPU (baseline) | Peak | Max Disk |
|--------|----------------|------|----------|
| 0.5 GB | 0.25 | 4x | 8 GB |
| 1 GB | 0.5 | 4x | 8 GB |
| 2 GB (default) | 1 | 4x | 8 GB |
| 4 GB | 2 | 4x | 16 GB |
| 8 GB | 4 | 4x | 32 GB |

### 10. Network connectors — ingress defaults

**Official**: Ingress connectors are AWS-managed. Defaults provided:
- `arn:aws:lambda:<region>:aws:network-connector:aws-network-connector:ALL_INGRESS`
- `arn:aws:lambda:<region>:aws:network-connector:aws-network-connector:INTERNET_EGRESS`
- `NO_INGRESS` available to disable inbound

Egress to VPC requires customer-managed connector (our MicroVMNetwork design is correct for this).

## What Our Design Gets Right ✅

- Pool/ReplicaSet abstraction (no AWS equivalent — this IS our value-add)
- Template abstraction (not in AWS — operator-only concept)
- Drift detection and self-healing (ACK doesn't do this)
- Webhook validation and mutation (ACK doesn't do this)
- Namespace quotas (multi-tenant governance)
- kubectl plugin (no AWS equivalent)
- Network Connector as reconciled resource (correct model for egress)
- Suspend/Resume lifecycle (matches official perfectly)
- Finalizer for cleanup (correct pattern)
- Auto-resume semantics (traffic-triggered, matches official)

## Impact on Implementation

### Code changes needed:
1. `MicroVMState` enum: remove STOPPED, STOPPING, PAUSED, STARTING, RESUMING
2. `DesiredState` enum: only Running, Suspended, Terminated
3. `DriftDetector`: simplify to only SUSPEND/RESUME/TERMINATE/RECREATE actions
4. `MicroVMClient` interface: rename to match official API operations
5. `MicroVMSpec`: remove `memorySizeMB`, `vcpus`; add `executionRoleArn`, `runHookPayload`, `logging`
6. `MicroVMImage` CRD: add (not yet implemented)
7. `MicroVMNetwork` CRD: already mostly correct

### CRD schema changes:
- `MicroVM.spec.memorySizeMB` → removed (inherited from image)
- `MicroVM.spec.executionRoleArn` → added
- `MicroVM.spec.runHookPayload` → added
- `MicroVMImage.spec.memorySizeMB` → added
- `MicroVMImage.spec.buildRoleArn` → added
- `MicroVMImage.spec.environmentVariables` → added
