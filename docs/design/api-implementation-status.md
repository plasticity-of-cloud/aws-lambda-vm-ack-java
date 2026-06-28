# AWS Lambda MicroVMs API — Implementation Status

Last updated: 2026-06-28

All 24 operations come from the botocore service model (`lambda-microvms/2025-09-09`).
See [sdk-client.md](sdk-client.md) for model provenance and endpoint details.

---

## MicroVM Lifecycle Operations

| API | Client method | Reconciler | CLI | Status |
|-----|---------------|------------|-----|--------|
| `RunMicrovm` | `DefaultMicroVMClient.runMicroVM()` | ✅ PENDING state | — | ✅ Complete |
| `GetMicrovm` | `DefaultMicroVMClient.getMicroVM()` | ✅ poll + drift | — | ✅ Complete |
| `SuspendMicrovm` | `DefaultMicroVMClient.suspendMicroVM()` | ✅ drift SUSPEND | `pause` | ✅ Complete |
| `ResumeMicrovm` | `DefaultMicroVMClient.resumeMicroVM()` | ✅ drift RESUME | `resume` | ✅ Complete |
| `TerminateMicrovm` | `DefaultMicroVMClient.terminateMicroVM()` | ✅ finalizer + TERMINATE drift | `delete` | ✅ Complete |
| `ListMicrovms` | ✅ `DefaultMicroVMClient.listMicroVMs()` | ❌ | `list` (k8s only) | ⚠️ CLI lists CRs, not AWS state |
| `CreateMicrovmAuthToken` | ✅ `DefaultMicroVMClient.createAuthToken()` | ❌ | ✅ `kubectl microvm token` | ✅ Complete |
| `CreateMicrovmShellAuthToken` | ✅ `DefaultMicroVMClient.createShellAuthToken()` | ❌ | ✅ `kubectl microvm exec` | ✅ Complete |
| `TagResource` | ✅ `DefaultMicroVMClient.tagResource()` | ✅ label sync on aligned | ❌ | ✅ Complete |
| `UntagResource` | ✅ `DefaultMicroVMClient.untagResource()` | ❌ | ❌ | ⚠️ Implemented, not yet wired for removals |
| `ListTags` | ✅ `DefaultMicroVMClient.listTags()` | ❌ | ❌ | ✅ Implemented |

---

## MicroVMImage Operations

| API | Client method | Reconciler | CLI | Status |
|-----|---------------|------------|-----|--------|
| `CreateMicrovmImage` | `MicroVMImageClient.createImage()` | ✅ first reconcile | `image create` | ✅ Complete |
| `UpdateMicrovmImage` | `MicroVMImageClient.updateImage()` | ✅ generation advance | `image update` | ✅ Complete |
| `GetMicrovmImage` | `MicroVMImageClient.getImage()` | ✅ poll image state | `image describe` | ✅ Complete |
| `DeleteMicrovmImage` | `MicroVMImageClient.deleteImage()` | ✅ finalizer | `image delete` | ✅ Complete |
| `GetMicrovmImageVersion` | `MicroVMImageClient.getImageVersion()` | ✅ poll version state | — | ✅ Complete |
| `UpdateMicrovmImageVersion` | ✅ `MicroVMImageClient.activateVersion()` | ✅ auto-activates on SUCCESSFUL | — | ✅ Complete |
| `DeleteMicrovmImageVersion` | ❌ not implemented | ❌ | — | ❌ Missing (version pruning) |
| `ListMicrovmImageVersions` | ✅ `MicroVMImageClient.listVersions()` | ✅ populates `status.versions[]` | `image describe` | ✅ Complete |
| `ListMicrovmImages` | ❌ not implemented | ❌ | `image list` (k8s only) | ⚠️ CLI lists CRs, not AWS state |
| `GetMicrovmImageBuild` | ✅ `MicroVMImageClient.getLatestBuild()` | ❌ | — | ✅ Implemented |
| `ListMicrovmImageBuilds` | ✅ (via `getLatestBuild`) | ❌ | — | ✅ Implemented |

---

## Managed Base Image Discovery

| API | Status | Notes |
|-----|--------|-------|
| `ListManagedMicrovmImages` | ❌ not implemented | Used in `setup-test-env.sh` via AWS CLI directly |
| `ListManagedMicrovmImageVersions` | ❌ not implemented | — |

---

## Priority Backlog

### P0 — Blocks end-to-end testing

**`CreateMicrovmAuthToken`**
- Without this, clients cannot connect to a running MicroVM (all endpoints require JWE token)
- Needed for: `kubectl microvm token` command, operator-surfaced connection info
- `CreateMicrovmAuthTokenRequest` fields: `microvmIdentifier`, `expirationInMinutes`, `allowedPorts`
- Response: `authToken` map (`X-aws-proxy-auth` header value)

### P1 — Feature correctness

**`UpdateMicrovmImageVersion` (activate/deactivate)**
- `MicroVMImageSpec.autoActivate=true` is stored but never acted upon
- After a version reaches `SUCCESSFUL`, the reconciler should call `UpdateMicrovmImageVersion --state ACTIVE`
- Currently Lambda auto-activates new versions, so this may work without us, but explicit activation is required for controlled rollouts

**`ListMicrovmImageVersions`**
- `status.versions[]` is defined in the CRD but never populated
- Needed for: `kubectl microvm image describe` showing version history

### P2 — Operational completeness

**Tag propagation (`TagResource` / `UntagResource`)**
- Sync `MicroVM` CR `.metadata.labels` → AWS resource tags
- Enables cost allocation, filtering by team/env in AWS console

**`ListMicrovms`**
- Currently `kubectl microvm list` only shows Kubernetes CRs
- Should reconcile AWS state with CR state (detect orphaned MicroVMs)

### P3 — Nice to have

**`CreateMicrovmShellAuthToken`**
- Powers `kubectl microvm exec` with native shell access
- Lower priority — gRPC/HTTP access covers most use cases

**`GetMicrovmImageBuild` / `ListMicrovmImageBuilds`**
- Exposes build logs and build history in `kubectl microvm image describe`
- Currently users must check CloudWatch directly

---

## Summary

| Category | Total | Implemented | Partial | Missing |
|----------|-------|-------------|---------|---------|
| MicroVM lifecycle | 11 | 10 | 1 | 0 |
| MicroVMImage | 11 | 9 | 1 | 1 |
| Base image discovery | 2 | 0 | 0 | 2 |
| **Total** | **24** | **19** | **2** | **3** |

## Remaining gaps

- `DeleteMicrovmImageVersion` — version pruning (low priority)
- `ListManagedMicrovmImages` / `ListManagedMicrovmImageVersions` — base image discovery (used via CLI directly)
- `UntagResource` tag removal not yet wired to reconciler (tags added but never removed)
