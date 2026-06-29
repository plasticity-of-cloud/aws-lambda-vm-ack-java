# What's Missing — Implementation Gaps

This document tracks features that are designed (or partially designed) but not yet implemented.
It is the authoritative TODO for engineering work beyond the initial API coverage.

---

## 1. MicroVMNetwork Reconciler

**Status**: Model and design exist. Reconciler not implemented.

**Revised design** (supersedes `docs/design/networking.md`):

VPC Network Connectors are managed via the `lambda-core` API (separate AWS service, distinct from `lambda-microvms`). The operator must generate a second SDK client from the `lambda-core` botocore service model, exactly as it did for `lambda-microvms`.

### SDK Client Generation

A new Maven module `operator-aws-client-core` is required:
- Feeds `/snap/aws-cli/.../botocore/data/lambda-core/2026-04-30/service-2.json` into `codegen-maven-plugin`
- Generates `LambdaCoreClient` / `LambdaCoreAsyncClient` in package `ai.codriverlabs.microvm.aws.lambdacore`
- `operator-controller` adds `operator-aws-client-core` as a dependency alongside `operator-aws-client`

`lambda-core` operations needed:
- `CreateNetworkConnector` — provision ENIs in customer VPC subnets
- `GetNetworkConnector` — poll PENDING → ACTIVE / FAILED
- `UpdateNetworkConnector` — update subnet/SG config
- `DeleteNetworkConnector` — cleanup on CR delete
- `ListNetworkConnectors` — discovery / drift detection

### MicroVMNetworkSpec (revised)

Current `MicroVMNetworkSpec` has `{vpcId, subnetIds, securityGroupIds}` which maps directly to `NetworkConnectorVpcEgressConfiguration`. Add `operatorRoleArn` and `networkProtocol`:

```yaml
spec:
  subnetIds:
    - subnet-abc123
    - subnet-def456
  securityGroupIds:
    - sg-xyz789
  operatorRoleArn: arn:aws:iam::864899852480:role/MicroVMNetworkConnectorRole
  networkProtocol: IPv4          # IPv4 | DualStack (default: IPv4)
  region: us-east-1
  tags:
    environment: production
```

Note: `vpcId` can be removed from spec — the Lambda Core API derives the VPC from the subnets. It is not a parameter to `CreateNetworkConnector`.

### MicroVMNetworkStatus (new class required)

```java
public class MicroVMNetworkStatus {
    String connectorArn;        // set after CreateNetworkConnector returns
    String connectorId;
    String connectorState;      // PENDING | ACTIVE | INACTIVE | FAILED | DELETING | DELETE_FAILED
    String stateReason;
    String stateReasonCode;
    String observedGeneration;
    List<Condition> conditions; // Ready=True when ACTIVE
}
```

### Reconciler Flow

```
CREATE:
  1. Call CreateNetworkConnector(name, subnetIds, securityGroupIds, operatorRoleArn, networkProtocol)
     → set status.connectorArn, status.connectorState=PENDING
  2. Poll GetNetworkConnector every 15s until ACTIVE or FAILED
  3. On ACTIVE: set condition Ready=True, reschedule at 5min
  4. On FAILED: set condition Ready=False with stateReason, emit Warning event

UPDATE (spec change detected via observedGeneration):
  1. Call UpdateNetworkConnector with new subnetIds/securityGroupIds
  2. Poll until ACTIVE or FAILED (same as create)
  Note: Cannot update while MicroVMs are running — webhook should warn; reconciler emits event

DELETE (finalizer: lambda.aws.amazon.com/network-connector-finalizer):
  1. Check no MicroVMs reference this network (list CRs with spec.networkRef=<name>)
  2. If in-use: block deletion, emit Warning event, requeue
  3. Call DeleteNetworkConnector
  4. Poll until state=DELETING completes (connector disappears from GetNetworkConnector → 404)
  5. Remove finalizer
```

### MicroVMNetworkClient wrapper

New class `MicroVMNetworkClient` in `operator-controller`:
```java
CompletableFuture<CreateNetworkConnectorResponse> createConnector(MicroVMNetworkSpec spec, String name);
CompletableFuture<GetNetworkConnectorResponse>    getConnector(String connectorArn);
CompletableFuture<UpdateNetworkConnectorResponse> updateConnector(String connectorArn, MicroVMNetworkSpec spec);
CompletableFuture<DeleteNetworkConnectorResponse> deleteConnector(String connectorArn);
CompletableFuture<List<NetworkConnectorSummary>>  listConnectors();
```

### MicroVM ↔ Network Association (unchanged)

When `MicroVM` has `spec.networkRef`, the `MicroVMReconciler`:
1. Looks up the referenced `MicroVMNetwork` CR
2. Checks `status.connectorState == ACTIVE`
3. Passes `status.connectorArn` as egress connector to `RunMicrovm`
4. If not ACTIVE: MicroVM stays PENDING with condition `NetworkReady=False`

### Lambda-Managed Connectors (no reconciliation needed)

For the standard AWS-managed connectors, users can reference them directly in `MicroVMSpec` without a `MicroVMNetwork` CR:

| Name | ARN |
|------|-----|
| `ALL_INGRESS` | `arn:aws:lambda:<region>:aws:network-connector:aws-network-connector:ALL_INGRESS` |
| `INTERNET_EGRESS` | `arn:aws:lambda:<region>:aws:network-connector:aws-network-connector:INTERNET_EGRESS` |
| `NO_INGRESS` | `arn:aws:lambda:<region>:aws:network-connector:aws-network-connector:NO_INGRESS` |

### IAM Requirements

The operator's pod identity role needs:
```json
{
  "Action": [
    "lambda:CreateNetworkConnector",
    "lambda:GetNetworkConnector",
    "lambda:UpdateNetworkConnector",
    "lambda:DeleteNetworkConnector",
    "lambda:ListNetworkConnectors"
  ],
  "Resource": "*",
  "Effect": "Allow"
}
```

The customer also needs a separate ENI provisioning role (`spec.operatorRoleArn`) trusted by `network-connectors.lambda.amazonaws.com` with `ec2:CreateNetworkInterface` + `ec2:CreateTags`.

### CLI

New subcommand group `kubectl microvm network`:
```bash
kubectl microvm network list
kubectl microvm network describe --name my-network
kubectl microvm network create --name my-network --subnets subnet-abc,subnet-def --security-groups sg-xyz --role arn:...
kubectl microvm network delete --name my-network
```

### What's missing (implementation checklist)

- [ ] New Maven module `operator-aws-client-core` with `lambda-core` codegen
- [ ] `MicroVMNetworkStatus` model class
- [ ] Update `MicroVMNetworkSpec`: remove `vpcId`, add `operatorRoleArn`, `networkProtocol`, `region`, `tags`
- [ ] `MicroVMNetworkClient` wrapper class
- [ ] `MicroVMNetworkReconciler` (create / poll / update / delete with finalizer)
- [ ] `MicroVMReconciler`: check `NetworkReady` condition before RunMicrovm (currently assumes status is populated)
- [ ] Validating webhook: check referenced `MicroVMNetwork` CR exists and is Ready
- [ ] IAM role update (`iam/kube-microvm-operator-role.yaml`) with lambda-core permissions
- [ ] IAM CloudFormation template for customer's ENI role
- [ ] `kubectl microvm network` CLI subcommands
- [ ] Integration tests: `MicroVMNetworkReconcilerIT`

---

## 2. MicroVMReplicaSet (renamed from MicroVMPool)

**Status**: Model, status class, and design exist under the old name `MicroVMPool`. Reconciler not implemented. **All code and docs must be renamed to `MicroVMReplicaSet`.**

**Rename scope:**
- `MicroVMPool.java` → `MicroVMReplicaSet.java`
- `MicroVMPoolSpec.java` → `MicroVMReplicaSetSpec.java`
- `MicroVMPoolStatus.java` → `MicroVMReplicaSetStatus.java`
- `docs/design/pool.md` → `docs/design/replicaset.md`
- CRD group/kind: `MicroVMPool` → `MicroVMReplicaSet`
- Helm chart CRDs

**What's missing after rename:**

| Gap | Detail |
|-----|--------|
| `MicroVMReplicaSetReconciler` | No reconciler class exists |
| Scale-up logic | Create child MicroVM CRs from `spec.template` when `currentReplicas < spec.replicas` |
| Scale-down logic | Select victims by policy (MostRecentFirst / OldestFirst / Random), set `desiredState: Terminated` |
| Rolling update logic | Detect `spec.template` generation change, create new / drain old maintaining `minReady` |
| Health-based eviction | Replace FAILED / stuck-PENDING / unexpectedly-TERMINATED MicroVMs |
| `spec.desiredPoolState` | Cascade `Suspended` / `Running` to all children |
| `MicroVMReplicaSetStatus` | Populate `readyReplicas`, `currentReplicas`, `suspendedReplicas`, `updatedReplicas` |
| Integration tests | `MicroVMReplicaSetReconcilerIT` does not exist |
| CLI | `kubectl microvm rs` / `kubectl microvm replicaset` subcommand does not exist |

**`MicroVMReplicaSetSpec` fields needed beyond current `MicroVMPoolSpec`:**
```yaml
spec:
  replicas: 5
  template: <MicroVMSpec>
  minReady: 1              # exists
  maxSurge: 2              # exists
  scaleDown:
    policy: MostRecentFirst   # MISSING
    stabilizationWindowSeconds: 60  # MISSING
  desiredPoolState: Running   # MISSING (Running | Suspended)
```

---

## 3. Token Injection (Phase 1 — Operator Sub-Resource)

**Status**: Full design in `docs/design/roadmap/token-injection.md`. Not implemented.

**Problem**: `kubectl microvm token` requires AWS credentials in the calling process. Pods inside the cluster that need to connect to MicroVMs cannot call AWS directly (no credentials, no IAM role).

**Solution**: Operator exposes a REST sub-resource endpoint. Pods call the operator via Kubernetes RBAC (ServiceAccount token), operator calls AWS and returns the MicroVM auth token.

**What's missing:**

| Gap | Detail |
|-----|--------|
| `/tokens` REST endpoint in operator | Quarkus REST endpoint: `POST /apis/lambda.aws.amazon.com/v1alpha1/namespaces/{ns}/microvms/{name}/tokens` |
| Token request CRD (optional) | Alternative: `MicroVMTokenRequest` CR (like `CertificateSigningRequest`) |
| RBAC policy | New ClusterRole `microvm-token-requester` allowing `create` on `microvms/tokens` subresource |
| CLI integration | `kubectl microvm token` should fall back to operator sub-resource when no AWS credentials in environment |
| Helm chart | ServiceAccount + RBAC for pods that need token access |

---

## 4. GraalVM Native CLI Binary

**Status**: `operator-cli/pom.xml` has a `native` Maven profile. Not built or distributed.

**What's missing:**

| Gap | Detail |
|-----|--------|
| Native build CI | GitHub Actions matrix: `linux/amd64` + `linux/arm64` (mandatory for macOS Apple Silicon users) |
| `install-kubectl-plugin.sh` native mode | Currently installs JVM runner JAR; needs to detect and install native binary when available |
| GraalVM reflection config | Quarkus auto-generates most of it, but AWS SDK model classes likely need manual `reflect-config.json` entries |
| Krew manifest | `plugins/kubectl-microvm.yaml` for Krew index submission |
| GHCR release artifact | Native binaries attached to GitHub Release as `kubectl-microvm-linux-amd64`, `kubectl-microvm-linux-arm64`, `kubectl-microvm-darwin-arm64` |

---

## 5. Helm Chart Publishing

**Status**: Chart exists at `charts/lambda-vm-ack-operator/`. Never published to GHCR OCI registry.

**What's missing:**

| Gap | Detail |
|-----|--------|
| `helm push` to GHCR | `oci://ghcr.io/plasticity-of-cloud/helm/kube-microvm-operator` referenced in README but empty |
| GitHub Actions release workflow | On tag push: build image → push ECR → package chart → push GHCR |
| Chart versioning | `Chart.yaml` version must be bumped in lockstep with operator image tag |
| CRD versioning | Chart CRDs need `helm.sh/chart-version` annotation for upgrade safety |

---

## Priority Order

| # | Feature | Effort | Value |
|---|---------|--------|-------|
| 1 | **MicroVMReplicaSet rename + reconciler** | Large | Core functionality — pool management |
| 2 | **MicroVMNetwork reconciler** | Medium | Required for VPC egress |
| 3 | **Token injection Phase 1** | Medium | Required for in-cluster pod access |
| 4 | **GraalVM native CLI + distribution** | Medium | Required for production CLI distribution |
| 5 | **Helm chart publishing** | Small | Required for anyone else to install |
