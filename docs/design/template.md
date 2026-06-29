# MicroVMTemplate — Design and Usage

**Status**: CRD model implemented (`MicroVMTemplate`, `MicroVMTemplateSpec`). Template resolution not implemented — deferred. See [Current State](#current-state) and [What Would Need to Change](#what-would-need-to-change).

---

## Purpose

`MicroVMTemplate` is a reusable configuration object — analogous to Kubernetes `PodTemplate`. It lets you define a standard MicroVM spec once and reference it from multiple `MicroVM` CRs or `MicroVMReplicaSet` pools, avoiding repetition and ensuring consistency.

Without templates, every MicroVM or ReplicaSet must inline the full spec:

```yaml
# Without templates — repeated in every MicroVM
spec:
  imageRef: arn:aws:lambda:us-east-1:123:microvm-image:python-sandbox
  executionRoleArn: arn:aws:iam::123:role/SandboxRole
  maxIdleDurationSeconds: 900
  suspendedDurationSeconds: 3600
  autoResumeEnabled: true
  networkRef: private-vpc
  egressNetworkConnectors:
    - arn:aws:lambda:us-east-1:123:network-connector:vpc-connector
```

With templates:

```yaml
# Define once
kind: MicroVMTemplate
metadata:
  name: python-sandbox
  namespace: team-alpha
spec:
  imageRef: arn:aws:lambda:us-east-1:123:microvm-image:python-sandbox
  executionRoleArn: arn:aws:iam::123:role/SandboxRole
  maxIdleDurationSeconds: 900
  suspendedDurationSeconds: 3600
  autoResumeEnabled: true
  networkRef: private-vpc

# Reference from a MicroVM (desired future state)
kind: MicroVM
metadata:
  name: tenant-123
spec:
  templateRef: python-sandbox   # inherits all fields above
  runHookPayload: "tenant-123"  # per-instance override

# Reference from a MicroVMReplicaSet (desired future state)
kind: MicroVMReplicaSet
metadata:
  name: sandbox-pool
spec:
  replicas: 10
  templateRef: python-sandbox   # inherits all fields above
```

---

## Current State

`MicroVMTemplate` CRD is defined but **template resolution is not implemented**. `spec.templateRef` does not exist on `MicroVMSpec` or `MicroVMReplicaSetSpec`.

**Current workaround**: embed the full spec inline in every `MicroVM` or `MicroVMReplicaSet`. This is sufficient for the initial release.

```yaml
# Current — embed spec directly in MicroVMReplicaSet
kind: MicroVMReplicaSet
spec:
  replicas: 5
  template:                     # full MicroVMSpec inlined here
    imageRef: arn:...
    executionRoleArn: arn:...
    maxIdleDurationSeconds: 900
    networkRef: private-vpc
```

The `MicroVMTemplate` CR can still be created and stored in the cluster — it is a valid Kubernetes resource. It just has no effect until resolution is implemented.

---

## What Would Need to Change

### 1. `MicroVMSpec` — add `templateRef` field

```java
// operator-core: MicroVMSpec.java
private String templateRef;   // name of MicroVMTemplate CR in the same namespace
```

```yaml
# User-facing
spec:
  templateRef: python-sandbox
  runHookPayload: "tenant-123"   # overrides only; all other fields from template
```

### 2. `MicroVMReplicaSetSpec` — add top-level `templateRef`

```java
// operator-core: MicroVMReplicaSetSpec.java
private String templateRef;   // alternative to spec.template (inline spec)
```

Either `templateRef` OR `template` (inline) is required — mutually exclusive. Webhook validates.

### 3. Mutating webhook — resolve `templateRef`

In `MicroVMMutatingWebhook`, after resolving `spec.className`, add:

```java
// If templateRef is set, fetch the MicroVMTemplate CR and merge its fields
if (spec.getTemplateRef() != null) {
    MicroVMTemplate tmpl = kubernetesClient.resources(MicroVMTemplate.class)
        .inNamespace(namespace).withName(spec.getTemplateRef()).get();
    if (tmpl != null) {
        mergeTemplateIntoSpec(spec, tmpl.getSpec());
    }
}
```

Merge semantics: template fields are defaults — any field already set on the `MicroVMSpec` takes precedence (same pattern as `MicroVMClass`).

### 4. Validating webhook — validate `templateRef` exists

In `MicroVMValidatingWebhook`:

```java
if (spec.getTemplateRef() != null) {
    MicroVMTemplate tmpl = kubernetesClient.resources(MicroVMTemplate.class)
        .inNamespace(namespace).withName(spec.getTemplateRef()).get();
    if (tmpl == null) {
        errors.add("spec.templateRef: MicroVMTemplate '" + spec.getTemplateRef()
            + "' not found in namespace '" + namespace + "'");
    }
}
```

### 5. `MicroVMReplicaSetReconciler` — resolve `templateRef` when creating child MicroVMs

Currently, child MicroVMs are created from `spec.template` (inline `MicroVMSpec`). When `templateRef` is supported, the reconciler must resolve it first:

```java
MicroVMSpec childSpec = rs.getSpec().getTemplate();
if (childSpec == null && rs.getSpec().getTemplateRef() != null) {
    MicroVMTemplate tmpl = k8s.resources(MicroVMTemplate.class)
        .inNamespace(ns).withName(rs.getSpec().getTemplateRef()).get();
    childSpec = toMicroVMSpec(tmpl.getSpec());
}
```

### 6. CLI — `kubectl microvm template` subcommands

```bash
kubectl microvm template list                    # list all MicroVMTemplates
kubectl microvm template describe --name X       # show spec
kubectl microvm template validate --name X       # dry-run: check all referenced resources exist
```

### 7. `MicroVMTemplateSpec` — align with `MicroVMSpec`

`MicroVMTemplateSpec` is currently missing several fields present in `MicroVMSpec`:
- `imageVersion` (specific version, not just imageRef)
- `runHookPayload` (should NOT be in template — it's always per-instance)
- `ingressNetworkConnectors` / `egressNetworkConnectors` (list form)
- `className` (a template can reference a MicroVMClass for runtime profile defaults)
- `region`
- `tags`

---

## Interaction with MicroVMClass

`MicroVMClass` (existing, implemented) and `MicroVMTemplate` serve different purposes:

| | `MicroVMClass` | `MicroVMTemplate` |
|-|----------------|-------------------|
| Scope | Runtime profile (idle policy, connectors, max duration) | Full spec (image, network, IAM role, runtime profile) |
| Granularity | Fine-grained policy overrides | Complete reusable configuration |
| Typical user | Platform admin sets classes; devs reference them | Platform admin defines templates; devs reference them |
| Nesting | Referenced from `MicroVMTemplate` or `MicroVMSpec.className` | Can include `spec.className` to reference a class |

Resolution order when both are used:
1. `spec.templateRef` → merge template fields as defaults
2. `spec.className` → merge class fields as defaults (may come from template)
3. Global webhook defaults (maximumDurationSeconds, autoResumeEnabled)
4. Explicit `spec` fields → always win

---

## Migration Path (when implemented)

Users currently using inline specs can migrate incrementally:

```bash
# Extract shared fields into a template
kubectl apply -f - <<EOF
kind: MicroVMTemplate
metadata: {name: my-template}
spec:
  imageRef: ...
  executionRoleArn: ...
EOF

# Update MicroVMs to use templateRef (webhook merges remaining fields)
kubectl patch microvm my-vm --type merge -p '{"spec":{"templateRef":"my-template"}}'
```

The webhook merge is non-destructive — existing fields on the `MicroVMSpec` are preserved.

---

## Files to Change When Implementing

| File | Change |
|------|--------|
| `operator-core/.../MicroVMSpec.java` | Add `templateRef` field |
| `operator-core/.../MicroVMReplicaSetSpec.java` | Add `templateRef` field (alternative to `template`) |
| `operator-core/.../MicroVMTemplateSpec.java` | Add missing fields (align with MicroVMSpec) |
| `operator-webhook/.../MicroVMMutatingWebhook.java` | Resolve `templateRef` → merge defaults |
| `operator-webhook/.../MicroVMValidatingWebhook.java` | Validate `templateRef` exists |
| `operator-controller/.../MicroVMReplicaSetReconciler.java` | Resolve `templateRef` when creating children |
| `operator-cli/.../MicroVMCommand.java` | Register `TemplateCommand` |
| `operator-cli/...` | Add `TemplateCommand` (list, describe, validate) |
| `charts/.../clusterrole.yaml` | Add `microvmtemplates` to operator RBAC |
