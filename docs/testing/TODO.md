# E2E Testing TODO — Remaining for GA

Last updated: 2026-07-01

## Completed ✅

| Feature | Branch | UAT Doc | Result |
|---------|--------|---------|--------|
| MicroVMImage create/build | v1.0.0-rc1 | e2e-test-plan.md | ✅ All pass |
| MicroVM run + terminate | v1.0.0-rc1 | e2e-test-plan.md | ✅ All pass |
| MicroVMNetwork (VPC egress) | feature/e2e-network-testing | e2e-test-plan.md | ✅ All pass |
| Networking modes (3 modes) | feature/outbound-connectivity-test | — | ✅ Internet + VPC + no-egress |
| imageRef resolution by CR name | feature/imageref-resolution | — | ✅ RBAC-enforced |
| networkRef resolution by CR name | feature/e2e-network-testing | — | ✅ Connector ARN resolved |
| Suspend / Resume | feature/e2e-suspend-resume | uat-suspend-resume.md | ✅ 4/4 core tests |
| Image Update (new version) | feature/e2e-image-update | uat-image-update.md | ✅ 5/5 tests |
| ReplicaSet scale + bug fix | feature/e2e-replicaset | uat-replicaset.md | ✅ Fixed + merged |
| kubectl microvm token --direct | v1.0.0-rc1 | — | ✅ Works |

## Remaining — Must Do for GA

### 1. Token via Operator Endpoint (no --direct flag)

**What**: Pod calls operator REST endpoint → operator calls AWS → returns token.
In-cluster flow without AWS credentials on the caller.

**Code status**: Done (7 integration tests pass — MicroVMTokenResourceIT)

**What to test on EKS**:
- Create a ServiceAccount with `microvms/token` RBAC
- Create a pod using that SA
- Pod calls `https://kube-microvm-operator.kube-microvm.svc:443/...token` with its SA token
- Receives MicroVM auth token back
- Uses token to call MicroVM endpoint

**Effort**: ~15 min (operator HTTPS already working on 8443)

### 2. Validating Webhook

**What**: Rejects invalid CRs at admission time (missing required fields, invalid values).

**Code status**: Exists but endpoint returns 404 on cluster. Path mismatch.

**What to fix**:
- Debug webhook endpoint path (`/validate-microvm`) — verify Quarkus serves it
- Ensure the service port routing (443→8443) works for webhooks
- Test: apply invalid CR → expect rejection message

**Effort**: ~20 min (likely just a path issue in the webhook handler registration)

### 3. Pod Sidecar Injection (Mutating Webhook)

**What**: Annotate a pod with `lambda.microvm.auth: <vm-name>`, webhook injects
auth-agent sidecar that auto-refreshes tokens.

**Code status**: Done (5 integration tests pass — PodMutatingWebhookIT)

**What to test on EKS**:
- Deploy operator with mutating webhook active
- Create a pod with the annotation
- Verify sidecar container injected
- Verify token file refreshed at `/var/run/microvm/token`

**Effort**: ~30 min (requires auth-agent image in ECR, webhook path working)

**Dependency**: Requires #2 (webhook) to be working first

### 4. Drift Detection

**What**: Operator detects when AWS state doesn't match desired state (e.g. VM
terminated externally) and reconciles.

**Code status**: Done (mocked in MicroVMReconcilerIT)

**What to test on EKS**:
- Create MicroVM via operator (Running)
- Terminate it directly via AWS CLI (bypassing operator)
- Verify operator detects the drift and updates CR status

**Effort**: ~10 min

### 5. Auto-Suspend / Auto-Resume (idle policy)

**What**: VM suspends after N seconds of no traffic, resumes when traffic arrives.

**Code status**: `maxIdleDurationSeconds` passed to RunMicrovm, but never E2E tested.

**What to test on EKS**:
- Create VM with `maxIdleDurationSeconds: 60`
- Wait 90s without sending traffic
- Verify state → Suspended
- Send traffic → verify auto-resume (if `autoResumeEnabled: true`)

**Effort**: ~5 min (just waiting)

## Nice to Have (Post-GA)

| Feature | Notes |
|---------|-------|
| `kubectl microvm exec` | Shell access via ShellAuthToken |
| ReplicaSet rolling update | Template change → gradual replacement |
| Cross-namespace imageRef | ClusterMicroVMImage resource |
| Build logs in CR status | Surface GetMicrovmImageBuild |
| `kubectl microvm image list-base` | ListManagedMicrovmImages in CLI |
| Krew manifest | kubectl plugin distribution |
| macOS native CLI | Darwin arm64 build |

## Priority Order for GA

1. **Token via operator** — enables in-cluster auth without AWS creds
2. **Validating webhook** — prevents invalid CRs from being created
3. **Drift detection** — quick win, ensures operator recovers from external changes
4. **Auto-suspend** — quick win, just needs waiting
5. **Pod sidecar injection** — depends on webhook fix
