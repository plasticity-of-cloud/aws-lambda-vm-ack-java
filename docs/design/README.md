# KubeMicroVM — Architecture & Design

## Overview

KubeMicroVM is a Kubernetes operator that provides a **platform layer** on top of AWS Lambda MicroVMs. While the official ACK controller (aws-controllers-k8s/lambdamicrovms-controller) provides 1:1 CRUD mapping of AWS resources to Kubernetes CRDs, KubeMicroVM delivers an opinionated, batteries-included experience for platform teams managing multi-tenant MicroVM workloads.

## Positioning vs ACK

| Capability | ACK lambdamicrovms-controller | KubeMicroVM |
|-----------|-------------------------------|-------------|
| MicroVM CRUD | ✅ (auto-generated) | ✅ (with state machine + drift detection) |
| MicroVM Image management | ✅ (raw API mapping) | ✅ (build pipeline abstraction) |
| Network Connector management | ✅ (raw API mapping) | ✅ (declarative, validated) |
| Pool/ReplicaSet semantics | ❌ | ✅ (MicroVMPool with scaling) |
| Template abstraction | ❌ | ✅ (MicroVMTemplate) |
| Drift detection & self-healing | ❌ | ✅ |
| Admission webhooks (validation, mutation, quota) | ❌ | ✅ |
| Suspend/resume lifecycle policies | ❌ | ✅ |
| kubectl plugin | ❌ | ✅ (kubectl-microvm) |
| GraalVM native image | ❌ (Go binary) | ✅ (sub-50ms startup) |

## Design Principles

1. **Declarative lifecycle** — Users declare desired state; the operator converges.
2. **Least privilege** — Operator requests only the IAM permissions it needs.
3. **Fail-safe** — Webhook validation prevents invalid resources from entering the cluster.
4. **Observable** — Prometheus metrics, Kubernetes events, structured logging.
5. **Multi-tenant** — Namespace-scoped quotas, RBAC aggregation, network isolation.

## Documents

| Document | Description |
|----------|-------------|
| [CRD Design](crds.md) | Custom Resource definitions and field semantics |
| [Networking](networking.md) | Network Connector integration and MicroVMNetwork design |
| [Lifecycle & State Machine](lifecycle.md) | MicroVM states, transitions, suspend/resume policies |
| [Pool Management](pool.md) | MicroVMPool scaling, rolling updates, health-based eviction |
| [Image Management](images.md) | MicroVMImage build pipeline and versioning |
| [Webhooks](webhooks.md) | Admission webhook validation, mutation, and quota enforcement |
| [IAM & Security](iam.md) | IAM roles, Pod Identity, least-privilege design |
| [Drift Detection](drift.md) | Reconciliation strategy and self-healing |
| [CLI Design](cli.md) | kubectl-microvm plugin commands and UX |
