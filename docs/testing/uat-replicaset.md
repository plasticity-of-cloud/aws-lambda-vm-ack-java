# UAT: MicroVMReplicaSet

## Test Cases

| ID | Test | Expected |
|----|------|----------|
| RS-01 | Create ReplicaSet with replicas=2 | 2 MicroVM CRs created, both Running |
| RS-02 | Scale up to replicas=3 | 1 new MicroVM created |
| RS-03 | Scale down to replicas=1 | 2 MicroVMs terminated |
| RS-04 | Delete ReplicaSet | All child MicroVMs terminated and deleted |

## Prerequisites

- MicroVMImage `hello-node` CREATED and ACTIVE
- Operator running on EKS

## Results (2026-07-01, EKS Auto Mode, us-east-1)

| ID | Result | Notes |
|----|--------|-------|
| RS-01 | ⚠️ PARTIAL | 2/2 reported ready but 4 VMs created (race condition — over-creation) |
| RS-02 | ⚠️ PARTIAL | Scaled to 5 desired but 10 VMs created (same race) |
| RS-03 | ✅ PASS | Scale-down selects victims, sets Terminating. Eventually converges. |
| RS-04 | ✅ PASS | Delete cascades — all child MicroVMs terminated and removed |

## Known Bug: Over-Creation Race Condition

The reconciler creates more VMs than desired when multiple reconcile cycles fire
before status is updated. Root cause: the reconciler counts Running VMs to determine
how many to create, but new VMs take time to appear in status. Multiple concurrent
reconcile events each see "not enough replicas" and create more.

**Fix needed**: Add a creation rate limiter or track pending creations in status
(e.g. `status.pendingCreations`) to prevent duplicate launches within a single
generation.

## Acceptance: PARTIAL

- Scale-up, scale-down, and delete all work functionally
- Over-creation bug needs fixing before GA
