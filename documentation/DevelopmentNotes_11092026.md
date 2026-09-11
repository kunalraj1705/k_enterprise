# KRB Enterprise — Development Notes
## Session: 2026-09-11

## Objective
Begin the practical Kubernetes deployment of KRB Enterprise and prepare the existing Ubuntu VM for a single-node Kubernetes learning cluster.

## Infrastructure Repository Decision
Kubernetes manifests will live in the infrastructure repository, not the Spring Boot application repository.

Target structure:
```text
~/workspace/
├── k_enterprise/
│   ├── pom.xml
│   ├── mvnw
│   ├── src/
│   └── documentation/
└── k_enterprise_infra/
    ├── docker/
    │   ├── Dockerfile
    │   └── .dockerignore
    ├── compose/
    │   └── compose.yaml
    └── kubernetes/
        └── base/
            ├── namespace.yaml
            ├── configmap.yaml
            ├── secret.yaml
            ├── deployment.yaml
            ├── service.yaml
            ├── ingress.yaml
            └── hpa.yaml
```

Kustomize overlays may be introduced later for UAT/PROD. Initial manifests will remain straightforward so each Kubernetes resource is understood first.

## Ubuntu VM Verification
```text
Ubuntu 26.04.1 LTS
x86_64
8 CPUs
7.2 GiB RAM
5.7 GiB available RAM
0 B swap
29 GiB available disk
```

The VM is sufficient for the planned single-node learning cluster.

## Existing Runtime
Docker:
```text
Docker 29.1.3
```

containerd:
```text
containerd 2.2.2
```

containerd service was confirmed active and running.

Docker remains available for existing KRB Docker/Compose work. Kubernetes will use containerd.

## Kubernetes Installation
The Kubernetes v1.37 APT repository was added and `apt-get update` completed successfully.

Installed and verified:
```text
kubeadm v1.37.0
kubelet v1.37.0
kubectl v1.37.0
```

## containerd Preparation
Initially `/etc/containerd/config.toml` did not exist.

Generated the default configuration:
```bash
sudo mkdir -p /etc/containerd
containerd config default | sudo tee /etc/containerd/config.toml > /dev/null
```

The required next change is:
```text
SystemdCgroup = true
```

Then:
1. Restart containerd.
2. Verify containerd is running.
3. Enable/start kubelet.
4. Run `kubeadm init`.
5. Install CNI networking.
6. Verify the cluster.

## Namespace
A namespace manifest was designed but **not applied**, because the Kubernetes cluster has not yet been initialized.

Planned file:
```text
k_enterprise_infra/kubernetes/base/namespace.yaml
```

Content:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: uat-core
```

Do not apply it until the cluster is initialized and verified.

## Current Progress
```text
Infrastructure directory decided   ✅
VM resources verified              ✅
containerd verified               ✅
Kubernetes repo configured        ✅
kubeadm installed                 ✅
kubelet installed                 ✅
kubectl installed                 ✅
containerd config generated       ✅
SystemdCgroup                     🟡 Next
kubeadm init                      ⬜
CNI networking                    ⬜
Cluster verification              ⬜
uat-core namespace                ⬜
KRB Deployment                    ⬜
Service                           ⬜
ConfigMap                         ⬜
Secret                            ⬜
PostgreSQL                        ⬜
Ingress                           ⬜
HPA                               ⬜
```

## Resume Point
Next session starts with:
```text
Configure containerd
→ restart containerd
→ enable kubelet
→ kubeadm init
→ install CNI
→ verify cluster
→ apply uat-core namespace
```

No changes are required to the existing KRB Spring Boot application or Docker/Compose deployment at this stage.
