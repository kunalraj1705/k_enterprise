# KRB Enterprise — Kubernetes Hands-on Development Notes
## Session Date: 2026-09-18

## Objective
Continue Kubernetes scheduling and network isolation hands-on work for KRB Enterprise. GitHub Actions → Kubernetes deployment integration was intentionally deferred to the next session.

## 1. Resource Scheduling Experiment
Created `scheduler-test` in namespace `uat-core` with:
```yaml
resources:
  requests:
    cpu: "20"
    memory: "1Gi"
```
Commands:
```bash
kubectl get pod scheduler-test -n uat-core
kubectl describe pod scheduler-test -n uat-core
```
Observed:
```text
STATUS: Pending
Node: <none>
PodScheduled: False
```
Scheduler event:
```text
0/1 nodes are available: 1 Insufficient cpu.
preemption: 0/1 nodes are available:
1 Preemption is not helpful for scheduling.
```
The single lab node has 8 CPU allocatable, so a 20-CPU request cannot fit. Preemption cannot solve this. The test Pod was deleted afterward.

## 2. nodeSelector Experiment
Tested:
```yaml
nodeSelector:
  kubernetes.io/os: linux
```
The Pod scheduled to `ubuntuos`.

Changed the selector to Windows. Attempting to update the existing Pod produced a Pod immutability error because `nodeSelector` cannot be changed after creation. The Pod was recreated and remained Pending.

Scheduler event:
```text
0/1 nodes are available:
1 node(s) didn't match Pod's node affinity/selector.
preemption: 0/1 nodes are available:
1 Preemption is not helpful for scheduling.
```

## 3. Node Affinity
Tested required node affinity for:
```text
kubernetes.io/os=linux
```
The Pod scheduled successfully.

Also tested preferred affinity with `weight: 100`. With the single-node lab, both valid rules use `ubuntuos`; the conceptual distinction is hard requirement vs preference.

## 4. Taints
Inspected the node:
```bash
kubectl describe node ubuntuos | grep -i taint
```
Result: no taints configured.
No taint was added because this is a single-node lab and the running KRB Enterprise environment should not be unnecessarily disrupted.

## 5. Workload/Service Inspection
Current workloads were inspected:
```bash
kubectl get pods -n uat-core -o wide
```
Observed two KRB Enterprise Pods and `postgres-0`, all on `ubuntuos`.

PostgreSQL labels:
```bash
kubectl get pod postgres-0 -n uat-core --show-labels
```
Key label:
```text
app=postgres
```
KRB Enterprise label:
```text
app=krb-enterprise
```

Services:
```bash
kubectl get svc -n uat-core
```
Observed:
```text
krb-enterprise  ClusterIP  10.103.207.143  8282/TCP
postgres        ClusterIP  10.98.7.36      5432/TCP
```

## 6. NetworkPolicy
Created `network-policy.yaml`:
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: krb-enterprise-network-policy
  namespace: uat-core
spec:
  podSelector:
    matchLabels:
      app: postgres
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: krb-enterprise
      ports:
        - protocol: TCP
          port: 5432
```

Applied:
```bash
kubectl apply -f network-policy.yaml
```

Verified:
```bash
kubectl get networkpolicy -n uat-core
kubectl describe networkpolicy krb-enterprise-network-policy -n uat-core
```

Intent:
```text
KRB Enterprise → PostgreSQL: TCP 5432 allowed
Other Pods → PostgreSQL: restricted
```
The cluster uses Calico, which supports NetworkPolicy enforcement.

## 7. Concepts Reviewed
Reviewed:
- Pod priority and preemption
- Pod anti-affinity
- SecurityContext
- RBAC
- ServiceAccounts
- Jobs/CronJobs
- Kubernetes architecture
- Deployment strategies
- Observability
- common Pending/CrashLoopBackOff/Service troubleshooting

## 8. Lab Constraint
The Kubernetes environment is a single-node lab (`ubuntuos`). True node-level HA and meaningful anti-affinity distribution cannot be demonstrated until additional nodes exist.

## 9. GitHub Actions Integration — Deferred
The KRB Enterprise GitHub Actions pipeline already builds/pushes the application image to GHCR. The next step is to integrate Kubernetes deployment into the existing UAT workflow.

Target:
```text
Git push
→ GitHub Actions
→ build/test
→ Docker image
→ GHCR
→ Kubernetes deployment update/apply
→ RollingUpdate
→ readiness verification
→ health check
```
The existing Git commit SHA image-tag convention should be retained; do not switch to `latest`.

## Session Outcome
Completed today:
- resource scheduling experiment
- nodeSelector experiment
- Pod immutability observation
- required/preferred node affinity
- taints/tolerations review
- priority/preemption review
- anti-affinity/security/RBAC/ServiceAccount concepts
- NetworkPolicy implementation
- final Kubernetes architecture/production review

Deferred to next session:
**GitHub Actions → Kubernetes deployment integration and automated UAT rollout verification.**
