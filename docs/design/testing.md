# Integration Testing Strategy

## Overview

Integration tests exercise the reconciler logic against an in-memory Kubernetes API using
[Fabric8 Kubernetes Mock Server](https://github.com/fabric8io/kubernetes-client/blob/main/doc/KubernetesClientWithMockWebServer.md)
with `crud=true` mode. AWS API calls (`MicroVMImageClient`, `MicroVMClient`) are mocked with Mockito.

No cluster, no Docker, no binary downloads required. Tests run in CI with standard `./mvnw verify`.

## Testing Layers

| Layer | Tool | What it tests |
|-------|------|---------------|
| Unit | JUnit5 + Mockito | Reconciler business logic, state machine, drift detection |
| Property | jqwik | State machine invariants, CRD serialization, webhook validation |
| Integration | Fabric8 Mock Server (`crud=true`) | Full reconcile loop: CR create → status patch → poll |

## How Fabric8 Mock Server Works

`@EnableKubernetesMockClient(crud=true)` starts an in-memory HTTP server that behaves like
a real Kubernetes API for CRUD operations. The `KubernetesClient` injected into the test
routes all calls (create, get, patch, list, watch) to this in-memory store.

```java
@EnableKubernetesMockClient(crud = true)
class MicroVMImageReconcilerIT {

    KubernetesClient client;   // injected by the extension

    @Test
    void createImage_patchesStatusWithArn() {
        // 1. Pre-register CRD
        // 2. Create MicroVMImage CR
        // 3. Call reconciler.reconcile()
        // 4. Assert status patched with imageArn + imageState
    }
}
```

## Why Not LocallyRunOperatorExtension / @KubeAPITest

`LocallyRunOperatorExtension` requires either:
- A real cluster (Kind / Minikube / EKS) for integration tests
- `@KubeAPITest` (downloads a `kube-apiserver` binary — not suitable for all CI environments)

Fabric8 mock server covers 90% of what we need (CR lifecycle, status patching, watch events)
without infrastructure dependencies.

## Test Modules

Tests live in `operator-tests/src/test/java/.../tests/integration/`.

### `MicroVMImageReconcilerIT`

Scenarios:
1. **Create** — `MicroVMImage` CR created → `createImage()` called → status patched with `imageArn`, `imageState=CREATING`
2. **Poll CREATING** — on second reconcile, `getImage()` returns `CREATED`, `getImageVersion()` returns `IN_PROGRESS` → status updated
3. **Poll SUCCESSFUL** — version reaches `SUCCESSFUL` → settled, no more polling
4. **Update** — spec changes (new s3Key, generation increments) → `updateImage()` called
5. **Delete** — CR deleted → finalizer calls `deleteImage()`

### `MicroVMReconcilerIT`

Scenarios:
1. **PENDING → RUNNING** — `runMicrovm()` called → status patched with `microvmId`, `state=RUNNING`
2. **Drift: RUNNING desired SUSPENDED** — `suspendMicrovm()` called → status `SUSPENDING`
3. **AWS throttle** — retryable error → status unchanged, reschedule
4. **Not found** → `runMicrovm()` called again to recreate

## Running Tests

```bash
# All tests including integration
./mvnw verify -pl operator-tests

# Skip integration tests
./mvnw verify -pl operator-tests -DskipITs
```
