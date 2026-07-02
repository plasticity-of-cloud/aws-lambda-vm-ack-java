# Pod Sidecar Token Injection

## Overview

When a pod is annotated with `lambda.microvm.auth: <vm-name>`, the KubeMicroVM
mutating webhook automatically injects a `microvm-auth-agent` sidecar that:

1. Authenticates to the operator token endpoint using the pod's ServiceAccount token
2. Calls AWS `CreateMicrovmAuthToken` on behalf of the pod
3. Writes the token to a shared in-memory volume at `/var/run/microvm/token`
4. Refreshes the token before expiry (default: every 5 minutes)

The main application container reads the token from the shared volume — no AWS
credentials or SDK needed in the application.

## Authentication Flow

```
Pod (SA token) → POST /apis/.../microvms/{name}/token → Operator
                                                         → TokenReview (k8s)
                                                         → SubjectAccessReview (k8s)
                                                         → AWS CreateMicrovmAuthToken
                                                         ← auth token
Sidecar writes token to emptyDir (Memory) volume
Main container reads /var/run/microvm/token
```

The pod's ServiceAccount token is used — no AWS credentials in the pod.
The operator validates the SA identity and RBAC before calling AWS.

## RBAC Requirement (Option A — Manual, GA)

The pod's ServiceAccount must have permission to `create` on `microvms/token`
for the specific VM. This is a **namespaced Role**, not a ClusterRole:

```yaml
# Grants SA 'my-app-sa' permission to get tokens for 'my-vm' only
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: microvm-token-my-vm
  namespace: default
rules:
- apiGroups: ["lambda.aws.amazon.com"]
  resources: ["microvms/token"]
  verbs: ["create"]
  resourceNames: ["my-vm"]  # scoped to this VM only
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: my-app-sa-microvm-token
  namespace: default
subjects:
- kind: ServiceAccount
  name: my-app-sa
  namespace: default
roleRef:
  kind: Role
  name: microvm-token-my-vm
  apiGroup: rbac.authorization.k8s.io
```

**Key points:**
- Use `resourceNames` to scope to a specific VM — least privilege
- The Role is namespaced — only grants access within one namespace
- One RoleBinding per SA + VM combination

## RBAC Auto-Provisioning (Option B — Post-GA)

In a future release, the mutating webhook will auto-create the Role+RoleBinding
when injecting the sidecar:

1. Webhook sees `lambda.microvm.auth: my-vm` annotation
2. Resolves the pod's ServiceAccount from the pod spec
3. Creates `Role` + `RoleBinding` in the namespace if not present
4. Injects sidecar as normal

This requires the operator SA to have `roles` and `rolebindings` write RBAC,
which increases its privilege surface. Deferred to post-GA.

## Enabling Sidecar Injection

### Step 1: Label the namespace

The `pod-inject` webhook only fires in namespaces labelled:

```bash
kubectl label namespace my-namespace lambda.microvm.auth/inject=enabled
```

### Step 2: Create SA + RBAC

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: my-app-sa
  namespace: default
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: microvm-token-my-vm
  namespace: default
rules:
- apiGroups: ["lambda.aws.amazon.com"]
  resources: ["microvms/token"]
  verbs: ["create"]
  resourceNames: ["my-vm"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: my-app-sa-microvm-token
  namespace: default
subjects:
- kind: ServiceAccount
  name: my-app-sa
  namespace: default
roleRef:
  kind: Role
  name: microvm-token-my-vm
  apiGroup: rbac.authorization.k8s.io
```

### Step 3: Annotate the pod

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-app
  namespace: default
  annotations:
    lambda.microvm.auth: my-vm           # VM name to get token for
    lambda.microvm.auth/mount-path: /var/run/microvm  # optional, default
    lambda.microvm.auth/expires: "30"    # optional, token expiry in minutes
spec:
  serviceAccountName: my-app-sa          # SA with RBAC above
  containers:
  - name: app
    image: my-app:latest
    # Token available at /var/run/microvm/token (read-only mount, injected)
    # Endpoint available at /var/run/microvm/endpoint
```

### What gets injected

The webhook adds:

1. **`microvm-token` emptyDir volume** (medium: Memory — token never hits disk)
2. **Volume mount** on all existing containers at `/var/run/microvm` (readOnly)
3. **`microvm-auth-agent` sidecar** with:
   - env: `MICROVM_NAME`, `MICROVM_NAMESPACE`, `MOUNT_PATH`, `TOKEN_EXPIRY_MINUTES`
   - volume mount at the same path (writable)
   - resource requests: 5m CPU, 16Mi memory

## Files Written by Sidecar

| File | Content |
|------|---------|
| `/var/run/microvm/token` | AWS auth token (`X-aws-proxy-auth` header value) |
| `/var/run/microvm/endpoint` | MicroVM endpoint hostname |
| `/var/run/microvm/expires_at` | Token expiry timestamp (ISO 8601) |

## Using the Token

```python
# Python example
with open("/var/run/microvm/token") as f:
    token = f.read().strip()
with open("/var/run/microvm/endpoint") as f:
    endpoint = f.read().strip()

import urllib.request
req = urllib.request.Request(f"https://{endpoint}/")
req.add_header("X-aws-proxy-auth", token)
response = urllib.request.urlopen(req)
```

```bash
# Shell example
TOKEN=$(cat /var/run/microvm/token)
ENDPOINT=$(cat /var/run/microvm/endpoint)
curl -H "X-aws-proxy-auth: $TOKEN" "https://$ENDPOINT/"
```

## Security Properties

- Token stored in memory only (`emptyDir medium: Memory`) — not on disk, not in etcd
- SA-level RBAC enforced by operator — each pod's SA can only access its own VMs
- Token expiry configurable (default 30 min), refreshed automatically
- Sidecar runs with minimal resources (5m CPU / 16Mi RAM)
- `FailurePolicy: Ignore` on the mutating webhook — pod creation never blocked
