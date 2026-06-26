# Webhooks

## Overview

The `operator-webhook` module provides admission webhooks that enforce validation, apply defaults, and manage quotas before resources are persisted to etcd.

## Mutating Webhook

**Trigger**: CREATE and UPDATE on `MicroVM` resources.

### Default Values Applied

| Field | Default | Condition |
|-------|---------|-----------|
| `spec.memorySizeMB` | 2048 | When null |
| `spec.maximumDurationSeconds` | 28800 | When null |
| `spec.desiredState` | Running | When null (on CREATE only) |
| `spec.idlePolicy.maxIdleDurationSeconds` | 900 | When idlePolicy present but field null |
| `spec.idlePolicy.suspendedDurationSeconds` | 3600 | When idlePolicy present but field null |
| `spec.idlePolicy.autoResumeEnabled` | true | When idlePolicy present but field null |

### Template Resolution

If `spec.templateRef` is set, the webhook resolves the `MicroVMTemplate` and fills in any unset fields from the template. User-specified values always take precedence over template values.

## Validating Webhook

**Trigger**: CREATE and UPDATE on `MicroVM`, `MicroVMPool`, `MicroVMNetwork`, `MicroVMImage` resources.

### MicroVM Validation

| Rule | Rejection message |
|------|-------------------|
| `memorySizeMB` not in {512, 1024, 2048, 4096, 8192} | "memorySizeMB must be one of: 512, 1024, 2048, 4096, 8192" |
| `maximumDurationSeconds` < 60 or > 28800 | "maximumDurationSeconds must be between 60 and 28800" |
| `idlePolicy.maxIdleDurationSeconds` < 60 or > 3600 | "maxIdleDurationSeconds must be between 60 and 3600" |
| `idlePolicy.suspendedDurationSeconds` < 60 or > 28800 | "suspendedDurationSeconds must be between 60 and 28800" |
| `imageRef.name` empty | "imageRef.name is required" |
| Referenced `MicroVMImage` does not exist | "MicroVMImage '<name>' not found in namespace '<ns>'" |
| Referenced `MicroVMImage` has no active version | "MicroVMImage '<name>' has no active version" |
| Referenced `MicroVMNetwork` does not exist | "MicroVMNetwork '<name>' not found in namespace '<ns>'" |
| Referenced `MicroVMNetwork` not in Active state | "MicroVMNetwork '<name>' connector is not active" |
| `desiredState` changed to invalid value on UPDATE | "desiredState must be Running, Suspended, or Terminated" |
| `desiredState: Running` when current is TERMINATED | "Cannot resume a terminated MicroVM" |

### MicroVMPool Validation

| Rule | Rejection message |
|------|-------------------|
| `replicas` < 0 or > 100 | "replicas must be between 0 and 100" |
| `maxSurge` < 0 or > 10 | "maxSurge must be between 0 and 10" |
| `template.imageRef.name` empty | "template.imageRef.name is required" |

### MicroVMNetwork Validation

| Rule | Rejection message |
|------|-------------------|
| `subnetIds` empty | "At least one subnetId is required" |
| `securityGroupIds` empty | "At least one securityGroupId is required" |
| `operatorRoleArn` not valid ARN format | "operatorRoleArn must be a valid IAM role ARN" |
| Subnet does not exist (ec2:DescribeSubnets) | "Subnet '<id>' not found" |
| Security group does not exist | "Security group '<id>' not found" |

### MicroVMImage Validation

| Rule | Rejection message |
|------|-------------------|
| `source.s3Bucket` empty | "source.s3Bucket is required" |
| `source.s3Key` empty | "source.s3Key is required" |
| `baseImageArn` not valid ARN format | "baseImageArn must be a valid Lambda MicroVM base image ARN" |
| `buildTimeout` < 60 or > 3600 | "buildTimeout must be between 60 and 3600" |

## Namespace Quota Enforcement

The validating webhook enforces per-namespace MicroVM quotas via a `ResourceQuota`-like annotation on the namespace:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: team-alpha
  annotations:
    lambda.aws.amazon.com/max-microvms: "20"
    lambda.aws.amazon.com/max-memory-mb: "40960"
```

On MicroVM CREATE:
1. Count existing MicroVMs in namespace (excluding Terminated)
2. If count >= `max-microvms`, reject with "Namespace quota exceeded: <count>/<max> MicroVMs"
3. Sum memory of existing MicroVMs + new request
4. If total > `max-memory-mb`, reject with "Namespace memory quota exceeded"

## Webhook Configuration

```yaml
# Mutating — runs first, applies defaults
failurePolicy: Ignore     # Don't block API server if webhook is down
sideEffects: None
timeoutSeconds: 5

# Validating — runs after mutation
failurePolicy: Fail       # Block invalid resources
sideEffects: None
timeoutSeconds: 10
```

## Webhook TLS

- cert-manager issues certificates for the webhook Service
- Certificate DNS names: `kube-microvm-webhook.<namespace>.svc`, `kube-microvm-webhook.<namespace>.svc.cluster.local`
- CA bundle injected into webhook configurations via `cert-manager.io/inject-ca-from` annotation
- Certificate rotation is automatic (renew 30 days before expiry)
