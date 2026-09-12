# KRB Enterprise — Kubernetes Infrastructure Development Notes
## Session: 2026-09-12

## Objective

Implement and validate the Kubernetes infrastructure for KRB Enterprise and PostgreSQL, including persistent storage, application replicas, Service networking, and Windows/Postman access.

## 1. Infrastructure Repository Structure

The Kubernetes base was bifurcated by application/workload:

```text
k_enterprise_infra/
└── kubernetes/
    └── base/
        ├── namespace.yaml
        ├── configmap.yaml
        │
        ├── postgres/
        │   ├── postgres-pv.yaml
        │   └── postgres.yaml
        │
        └── krbenterprise/
            ├── deployment.yaml
            └── service.yaml
```

The application-specific subfolder convention will be used as additional applications are introduced.

## 2. PostgreSQL PersistentVolume

File:

```text
postgres/postgres-pv.yaml
```

Configuration:

```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: postgres-pv
spec:
  capacity:
    storage: 5Gi
  accessModes:
    - ReadWriteOnce
  persistentVolumeReclaimPolicy: Retain
  hostPath:
    path: /var/lib/kubernetes/postgres
    type: DirectoryOrCreate
```

This provides 5Gi of node-backed storage for the single-node lab.

Node-side storage path:

```text
/var/lib/kubernetes/postgres
```

The `hostPath` approach is specific to the current single-node lab.

## 3. PostgreSQL StatefulSet and Service

File:

```text
postgres/postgres.yaml
```

The file contains:

1. PostgreSQL Service
2. PostgreSQL StatefulSet

### Service

```text
name: postgres
port: 5432
```

It provides the stable database endpoint:

```text
postgres:5432
```

### StatefulSet

```text
name: postgres
replicas: 1
image: postgres:18
database: krb_enterprise
username: krb
```

The container mounts:

```text
/var/lib/postgresql
```

using the volume named:

```text
postgres-data
```

The StatefulSet creates the PVC through:

```yaml
volumeClaimTemplates:
  - metadata:
      name: postgres-data
```

Requested storage:

```text
5Gi
ReadWriteOnce
```

Storage chain:

```text
postgres-0
   ↓
postgres-data-postgres-0 (PVC)
   ↓
postgres-pv (PV)
   ↓
/var/lib/kubernetes/postgres
```

Inside the PostgreSQL container, the volume is mounted at:

```text
/var/lib/postgresql
```

## 4. PostgreSQL Scheduling Issue

Initial state:

```text
postgres-0   Pending
```

Scheduler event:

```text
pod has unbound immediate PersistentVolumeClaims
```

Cause:

```text
PVC requested by StatefulSet
        ↓
No matching PV
        ↓
PVC remained unbound
        ↓
postgres-0 remained Pending
```

Creating and applying `postgres-pv.yaml` resolved the storage binding.

Final state:

```text
postgres-0   1/1   Running
```

## 5. KRB Enterprise Deployment

KRB Enterprise was configured with:

```text
replicas: 2
```

Observed Pods:

```text
krb-enterprise-676f765bcf-bds2m
192.168.207.135:8282

krb-enterprise-676f765bcf-s8d8q
192.168.207.136:8282
```

Both were:

```text
READY      1/1
STATUS     Running
RESTARTS   0
```

PostgreSQL was:

```text
postgres-0
READY      1/1
STATUS     Running
```

All workloads are on the single Kubernetes node `ubuntuos`, as expected for the current lab.

## 6. KRB Enterprise Service

File:

```text
krbenterprise/service.yaml
```

Configuration:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: krb-enterprise
  namespace: uat-core
spec:
  type: ClusterIP
  selector:
    app: krb-enterprise
  ports:
    - port: 8282
      targetPort: 8282
```

Current Service:

```text
NAME             TYPE        CLUSTER-IP       PORT(S)
krb-enterprise   ClusterIP   10.103.207.143   8282/TCP
```

The selector:

```yaml
app: krb-enterprise
```

matches the labels on the two application Pods.

## 7. Service Endpoint Verification

Command used:

```bash
kubectl get endpoints -n uat-core krb-enterprise
```

Result:

```text
192.168.207.135:8282
192.168.207.136:8282
```

This confirmed that the Service has both application Pods as endpoints.

Kubernetes returned a warning that the legacy `Endpoints` API is deprecated in Kubernetes 1.33+ and EndpointSlice is the preferred API.

## 8. Windows/Postman Access

The KRB Enterprise Service is a `ClusterIP`, so it is not directly exposed to Windows.

Current lab access:

```bash
kubectl port-forward --address 0.0.0.0 -n uat-core svc/krb-enterprise 8282:8282
```

The `--address 0.0.0.0` option was required because default port-forwarding listens only on Ubuntu's loopback interface.

The resulting path is:

```text
Windows / Postman
        ↓
VirtualBox NAT :8282
        ↓
Ubuntu VM 10.0.2.15:8282
        ↓
kubectl port-forward
        ↓
KRB Enterprise Service
        ↓
KRB Enterprise Pods
```

Postman endpoint:

```text
GET http://localhost:8282/krbenterprise/actuator/health
```

Windows access was successfully validated.

## 9. VirtualBox NAT Rule

Existing VirtualBox rule:

```text
Name:      KRB_ENTERPRISE_APP
Protocol:  TCP
Host IP:   0.0.0.0
Host Port: 8282
Guest IP:  10.0.2.15
Guest Port: 8282
```

This rule remains unchanged.

The missing piece was making the Kubernetes port-forward listen on:

```text
0.0.0.0:8282
```

rather than only:

```text
127.0.0.1:8282
```

## 10. Current End-to-End Architecture

```text
Windows / Postman
        │
        ▼
VirtualBox NAT :8282
        │
        ▼
Ubuntu VM :8282
        │
        ▼
kubectl port-forward
        │
        ▼
KRB Enterprise Service
ClusterIP :8282
        │
        ├──────────────┐
        ▼              ▼
     Pod 1           Pod 2
        │              │
        └──────┬───────┘
               │
               ▼
       PostgreSQL Service
          postgres:5432
               │
               ▼
           postgres-0
               │
               ▼
              PVC
               │
               ▼
              PV
               │
               ▼
/var/lib/kubernetes/postgres
```

## 11. Current Resource State

Namespace:

```text
uat-core
```

Pods:

```text
krb-enterprise-676f765bcf-bds2m   1/1 Running
krb-enterprise-676f765bcf-s8d8q   1/1 Running
postgres-0                        1/1 Running
```

Services:

```text
krb-enterprise   ClusterIP   10.103.207.143   8282/TCP
postgres         ClusterIP   10.98.7.36       5432/TCP
```

KRB Enterprise:

```text
2 replicas
```

PostgreSQL:

```text
1 StatefulSet replica
5Gi persistent storage
```

## 12. Implementation Decisions

### Application organization

Kubernetes manifests are separated into application-specific subfolders.

### KRB Enterprise

```text
Deployment + Service
```

### PostgreSQL

```text
StatefulSet + Service + PVC + PV
```

### External lab access

```text
VirtualBox NAT + kubectl port-forward
```

This is a lab mechanism. Production exposure will use the Kubernetes ingress/load-balancing architecture introduced later.

## 13. Next Implementation Area

The current baseline is operational.

Potential next Kubernetes implementation steps:

```text
Ingress
HPA
Kustomize overlays
UAT configuration separation
Secrets management
GitHub Actions → Kubernetes deployment
```
