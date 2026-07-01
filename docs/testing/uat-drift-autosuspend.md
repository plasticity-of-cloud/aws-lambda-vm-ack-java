# UAT: Drift Detection + Auto-Suspend

## Drift Detection Test Cases

| ID | Test | Steps | Expected |
|----|------|-------|----------|
| DRIFT-01 | External terminate detected | Create VM via operator, call `aws lambda-microvms terminate-microvm` directly, wait for reconcile | CR status transitions to Terminated |
| DRIFT-02 | Operator re-creates on drift | Create VM, externally terminate, verify CR reconciled | New VM ID in status OR status=Terminated matching desired=Terminated |

## Auto-Suspend Test Cases

| ID | Test | Steps | Expected |
|----|------|-------|----------|
| AUTO-01 | VM auto-suspends after idle | Create VM with `maxIdleDurationSeconds: 60`, send no traffic, wait 90s | `status.state` → Suspended |
| AUTO-02 | VM auto-resumes on traffic | After AUTO-01, send request to endpoint | Request succeeds, state → Running |

## Prerequisites

- Operator running on EKS
- MicroVMImage `hello-node` CREATED and ACTIVE
- Validating webhook deleted
