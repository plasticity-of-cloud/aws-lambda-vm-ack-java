# Custom Resource Definitions

## API Group

All resources use the API group `lambda.aws.amazon.com` with version `v1alpha1`, following the ACK convention for AWS service resources managed by third-party controllers.

## Resources

### MicroVM

The primary resource. Each MicroVM CR maps to a single AWS Lambda MicroVM instance.

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVM
metadata:
  name: dev-sandbox-01
  namespace: team-alpha
spec:
  # Image reference (required)
  imageRef:
    name: python-sandbox        # Reference to MicroVMImage CR in same namespace
    version: 3                  # Specific image version (optional, defaults to latest ACTIVE)

  # Compute resources
  memorySizeMB: 2048            # 512, 1024, 2048, 4096, 8192
  vcpus: 1                      # Derived from memory (0.25, 0.5, 1, 2, 4)

  # Networking
  networkRef:
    name: private-vpc           # Reference to MicroVMNetwork CR

  # Lifecycle
  desiredState: Running         # Running | Suspended | Terminated
  idlePolicy:
    maxIdleDurationSeconds: 900
    suspendedDurationSeconds: 3600
    autoResumeEnabled: true
  maximumDurationSeconds: 28800 # 8 hours max

  # Template reference (optional — overrides above fields)
  templateRef:
    name: standard-sandbox

  # Tags propagated to AWS resource
  tags:
    team: alpha
    environment: development
    cost-center: eng-platform

status:
  state: Running                # Pending | Running | Suspending | Suspended | Terminating | Terminated | Failed
  microVmId: "mvm-0abc123def456"
  endpointUrl: "https://mvm-0abc123def456.lambda-url.us-east-1.on.aws"
  ipAddress: ""                 # Not applicable (service-managed endpoint)
  imageVersion: 3
  lastTransitionTime: "2026-06-25T10:30:00Z"
  observedGeneration: 2
  conditions:
    - type: Ready
      status: "True"
      reason: Running
      message: "MicroVM is running and accepting traffic"
    - type: ImageReady
      status: "True"
      reason: ActiveVersion
      message: "Image version 3 is active"
    - type: NetworkReady
      status: "True"
      reason: ConnectorActive
      message: "Network connector is active"
```

### MicroVMPool

Manages a set of identical MicroVMs, similar to ReplicaSet/Deployment semantics.

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVMPool
metadata:
  name: ci-runners
  namespace: platform
spec:
  replicas: 5
  maxSurge: 2
  minReady: 3

  # Template for child MicroVMs
  template:
    imageRef:
      name: ci-runner-image
    memorySizeMB: 4096
    networkRef:
      name: ci-vpc
    idlePolicy:
      maxIdleDurationSeconds: 300
      autoResumeEnabled: false
    tags:
      pool: ci-runners

  # Scaling behavior
  scaleDown:
    stabilizationWindowSeconds: 300
    policy: MostRecentFirst      # MostRecentFirst | OldestFirst | Random

status:
  readyReplicas: 5
  currentReplicas: 5
  desiredReplicas: 5
  suspendedReplicas: 0
  observedGeneration: 1
  conditions:
    - type: Ready
      status: "True"
      reason: AllReplicasReady
      message: "5/5 replicas ready"
```

### MicroVMTemplate

Reusable spec template. Not reconciled — purely a Kubernetes-side reference.

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVMTemplate
metadata:
  name: standard-sandbox
  namespace: team-alpha
spec:
  memorySizeMB: 2048
  imageRef:
    name: python-sandbox
  networkRef:
    name: private-vpc
  idlePolicy:
    maxIdleDurationSeconds: 900
    suspendedDurationSeconds: 3600
    autoResumeEnabled: true
  maximumDurationSeconds: 28800
  tags:
    managed-by: kube-microvm-operator
```

### MicroVMNetwork

Declarative wrapper around an AWS Lambda Network Connector for egress. Reconciled — the operator creates/manages the underlying Network Connector resource.

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVMNetwork
metadata:
  name: private-vpc
  namespace: team-alpha
spec:
  # Egress configuration — maps to AWS Network Connector
  subnetIds:
    - subnet-0abc123
    - subnet-0def456
  securityGroupIds:
    - sg-0abc123
  networkProtocol: IPv4          # IPv4 | DualStack
  operatorRoleArn: "arn:aws:iam::123456789012:role/NetworkConnectorOperatorRole"

  # Ingress (informational — managed by AWS)
  # Each MicroVM gets its own HTTPS endpoint; no user config needed for inbound.

status:
  connectorArn: "arn:aws:lambda:us-east-1:123456789012:network-connector:my-connector"
  connectorState: Active         # Pending | Active | Inactive | Failed | Deleting
  lastSyncTime: "2026-06-25T10:00:00Z"
  conditions:
    - type: Ready
      status: "True"
      reason: ConnectorActive
      message: "Network connector is active"
```

### MicroVMImage

Manages the MicroVM image build lifecycle. Each image version represents a Firecracker snapshot built from a Dockerfile.

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVMImage
metadata:
  name: python-sandbox
  namespace: team-alpha
spec:
  # Source artifact (S3 zip containing Dockerfile + application code)
  source:
    s3Bucket: my-artifacts-bucket
    s3Key: images/python-sandbox-v3.zip

  # Base image (Lambda-managed)
  baseImageArn: "arn:aws:lambda:us-east-1::microvm-base-image:al2023/x86_64/standard:latest"

  # Build configuration
  buildTimeout: 600              # seconds
  readyHookEnabled: true         # Wait for /ready signal during build

  # Version management
  autoActivate: true             # Automatically activate successful builds

status:
  latestVersion: 3
  activeVersion: 3
  imageArn: "arn:aws:lambda:us-east-1:123456789012:microvm-image:python-sandbox"
  versions:
    - version: 3
      state: Active              # Pending | InProgress | Successful | Failed | Active | Inactive
      builtAt: "2026-06-25T09:00:00Z"
    - version: 2
      state: Inactive
      builtAt: "2026-06-20T09:00:00Z"
  conditions:
    - type: Ready
      status: "True"
      reason: ActiveVersionAvailable
      message: "Version 3 is active and ready"
```

## Field Validation Rules

| Resource | Field | Constraint |
|----------|-------|-----------|
| MicroVM | `memorySizeMB` | One of: 512, 1024, 2048, 4096, 8192 |
| MicroVM | `maximumDurationSeconds` | 60–28800 (1 min to 8 hours) |
| MicroVM | `idlePolicy.maxIdleDurationSeconds` | 60–3600 |
| MicroVM | `idlePolicy.suspendedDurationSeconds` | 60–28800 |
| MicroVM | `desiredState` | Running, Suspended, Terminated |
| MicroVMPool | `replicas` | 0–100 |
| MicroVMPool | `maxSurge` | 0–10 |
| MicroVMNetwork | `networkProtocol` | IPv4, DualStack |
| MicroVMImage | `buildTimeout` | 60–3600 |

## Owner References

- `MicroVMPool` → owns child `MicroVM` resources (cascade delete)
- `MicroVM` → references `MicroVMImage`, `MicroVMNetwork`, `MicroVMTemplate` (non-owning)
