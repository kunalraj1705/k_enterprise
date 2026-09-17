# KRB Enterprise — Kubernetes Development Notes
## Session Date: 2026-09-17

## Today's Hands-on Work

Today's work focused on implementing and testing a **PodDisruptionBudget (PDB)** and observing its interaction with the existing Deployment and HPA.

---

## 1. Created the KRB Enterprise PDB

File:

```text
k_enterprise_infra/kubernetes/base/krbenterprise/pdb.yaml
```

Configuration:

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: krb-enterprise
  namespace: uat-core
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: krb-enterprise
```

The selector matches the KRB Enterprise Pod label:

```text
app=krb-enterprise
```

---

## 2. Initial PDB Verification

The PDB initially reported:

```text
MIN AVAILABLE       2
MAX UNAVAILABLE     N/A
ALLOWED DISRUPTIONS 0
```

At that point KRB Enterprise had:

```text
Current available = 2
Desired available = 2
Total = 2
```

Therefore:

```text
2 available - 2 required = 0 allowed disruptions
```

This demonstrated that with exactly two available Pods, the PDB prevents a voluntary eviction from reducing availability below two.

---

## 3. Scaled KRB Enterprise for the PDB Experiment

The Deployment was temporarily scaled:

```bash
kubectl scale deployment krb-enterprise   -n uat-core   --replicas=5
```

During the transition, some Pods were still being created or terminated.

The PDB temporarily reported values such as:

```text
Current: 4
Desired: 2
Total: 5
Allowed disruptions: 2
```

Once the workload stabilized, the PDB reported:

```text
MIN AVAILABLE       2
ALLOWED DISRUPTIONS 3
```

This matched the expected relationship:

```text
5 available - 2 required = 3 allowed disruptions
```

---

## 4. Verified Pod Placement

The KRB Enterprise Pods were all scheduled on the same node:

```text
ubuntuos
```

The cluster is currently a single-node Kubernetes lab.

This is important because a node-drain experiment cannot demonstrate replacement Pods being rescheduled onto another worker node.

---

## 5. Direct Pod Deletion Experiment

A KRB Enterprise Pod was directly deleted:

```bash
kubectl delete pod <pod-name>   -n uat-core   --grace-period=0
```

The Deployment subsequently maintained the workload by creating replacement Pods.

Important lesson:

> Direct Pod deletion is not the correct way to test PDB enforcement.

A direct deletion does not go through the Kubernetes eviction mechanism used for PDB enforcement.

---

## 6. Node Drain / PDB Enforcement Test

The node was cordoned and a targeted drain was started:

```bash
kubectl drain ubuntuos   --ignore-daemonsets   --delete-emptydir-data   --pod-selector=app=krb-enterprise
```

The drain attempted to evict the KRB Enterprise Pods.

The PDB allowed the first voluntary disruptions while the number of available Pods remained at or above two.

Observed successful evictions included:

```text
krb-enterprise-96f7cd89c-n9ncn
krb-enterprise-96f7cd89c-sgstg
```

Further eviction attempts were rejected with:

```text
Cannot evict pod as it would violate the pod's disruption budget.
```

This was the key hands-on proof that the PDB was actively enforcing:

```yaml
minAvailable: 2
```

---

## 7. Node Became Cordoned

During the drain:

```text
ubuntuos   Ready,SchedulingDisabled
```

This means the node was cordoned and new Pods could not normally be scheduled onto it.

The drain was stopped because this is a single-node lab and the remaining Pods could not be rescheduled onto another worker node.

---

## 8. Uncordoned the Node

The node was restored with:

```bash
kubectl uncordon ubuntuos
```

Verification:

```text
ubuntuos   Ready
```

The node returned to normal scheduling status.

---

## 9. Deployment Recovery

After the node was uncordoned, KRB Enterprise returned to:

```text
READY        4/4
UP-TO-DATE   4
AVAILABLE    4
```

The Deployment controller maintained the application state and replacement Pods became available again.

---

## Key Hands-on Findings

### PDB enforcement

```text
Available Pods
      ↓
PDB checks voluntary eviction
      ↓
Enough availability?
   ┌───────┴───────┐
  Yes              No
   ↓                ↓
Eviction          Eviction
allowed           blocked
```

### PDB vs Deployment

```text
PDB
→ Prevents excessive voluntary disruption

Deployment
→ Maintains desired replicas

Scheduler
→ Places replacement Pods
```

### Single-node limitation

All KRB Enterprise Pods were on:

```text
ubuntuos
```

Therefore this lab cannot demonstrate real cross-node failover.

A multi-node cluster would allow:

```text
Node A
  ↓
Pod eviction
  ↓
Scheduler
  ↓
Node B
  ↓
Replacement Pod
```

---

## Final UAT State

At the end of the session:

```text
Node:
ubuntuos → Ready

KRB Enterprise:
4/4 Ready
4/4 Available

PDB:
minAvailable = 2
```

## Configuration Added

```text
k_enterprise_infra/
└── kubernetes/
    └── base/
        └── krbenterprise/
            └── pdb.yaml
```

## Important Note

The PDB configuration was created for the current UAT/lab environment.

No production high-availability conclusion was drawn from the single-node topology. The current cluster limits realistic cross-node disruption and rescheduling testing.

## Next Hands-on Topic

**Kubernetes Scheduling**

Planned practical work:

- CPU/memory requests and scheduling
- node capacity
- taints and tolerations
- node affinity
- Pod anti-affinity
- topology spread constraints
