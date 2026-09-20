# KRB Enterprise — Development Notes

**Date:** 20 September 2026  
**Milestone:** GitHub Actions → Kubernetes UAT deployment  
**Status:** COMPLETE

## 1. Objective
Integrate the KRB Enterprise CI pipeline with Kubernetes UAT using GitHub Actions and a self-hosted runner, without changing the application architecture.

## 2. Repository Structure

```text
~/workspace/
├── k_enterprise/
│   ├── .github/workflows/ci.yaml
│   └── application source
└── k_enterprise_infra/
    ├── docker/
    │   └── Dockerfile
    ├── compose/
    │   └── compose.yaml
    └── kubernetes/
        └── base/
```

The workflow lives in `k_enterprise` and checks out the infrastructure repository separately.

## 3. CI/CD Flow

```text
Git push to main
→ GitHub Actions
→ Java 25 + Maven clean verify
→ Docker Buildx
→ Push to GHCR
→ Self-hosted UAT runner
→ kubectl set image
→ Kubernetes RollingUpdate
→ application verification
```

## 4. Image Strategy
GHCR repository:

`ghcr.io/kunalraj1705/krbenterprise`

Images are tagged with:
- `latest`
- Git commit SHA

Kubernetes deployment uses the immutable Git SHA tag:

`ghcr.io/kunalraj1705/krbenterprise:${{ github.sha }}`

## 5. Self-Hosted Runner
Runner:
- User: `github-runner`
- Location: `/opt/github-runner`
- Runs locally on the UAT Kubernetes machine
- Has `kubectl` access

The initial problem was that `github-runner` had no kubeconfig. Kubernetes access was then configured for the dedicated runner user.

Production improvement identified: use a dedicated least-privilege Kubernetes identity instead of broad kubeconfig access.

## 6. UAT Environment
Namespace: `uat-core`

Application:
- Deployment: `krb-enterprise`
- Service: `krb-enterprise:8282`
- Context path: `/krbenterprise`

Database:
- StatefulSet: `postgres`
- Service: `postgres:5432`

Ingress:
- Host: `uat-customer.krbenterprise.com`
- HTTP NodePort: `31851`
- HTTPS NodePort: `30419`

Node:
- `ubuntuos`
- `10.0.2.15`

## 7. PostgreSQL Lifecycle Decision
PostgreSQL is intentionally managed separately from application deployment.

GitHub Actions does not:
- Start PostgreSQL
- Stop PostgreSQL
- Scale PostgreSQL
- Modify the PostgreSQL StatefulSet

For UAT, PostgreSQL is started independently when required:

```bash
kubectl scale statefulset/postgres --replicas=1 -n uat-core
```

## 8. Application Deployment
Application image is updated with:

```bash
kubectl set image deployment/krb-enterprise   krb-enterprise="$IMAGE"   -n uat-core
```

Then rollout is monitored:

```bash
kubectl rollout status deployment/krb-enterprise   -n uat-core   --timeout=180s
```

## 9. Replica Behavior
A deployment should not blindly set replicas to two.

Desired behavior:
- Existing N replicas → preserve N.
- Zero replicas → allow deployment to bring the application back to its baseline running state.

This avoids accidentally reducing a deployment that is already scaled to four or five replicas.

## 10. Health Verification
External UAT health endpoint:

```text
http://10.0.2.15:31851/krbenterprise/actuator/health
```

with:

```text
Host: uat-customer.krbenterprise.com
```

Observed during testing:
- `503` when the application had zero Pods.
- Temporary `502` while Ingress/upstreams were transitioning.
- Successful response once the application was ready.

Successful health response:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

The deployment verification design should use:
1. Kubernetes rollout/readiness for new Pods.
2. A stabilization period before external health checking.
3. External health check to validate the complete Ingress path.

## 11. Deployment Failure and Recovery
Initial deployment failed because KRB Enterprise was still at zero replicas.

Observed:

```text
No resources found in uat-core namespace.
```

and:

```text
krb-enterprise   0/0
```

The image was successfully updated, but no Pods could be created while the Deployment remained scaled to zero.

PostgreSQL was started separately, the application Deployment was started, and the pipeline subsequently succeeded.

This verified:
- Maven build/test
- Docker build
- GHCR push
- Self-hosted runner
- Kubernetes authentication
- `kubectl set image`
- RollingUpdate
- Ingress health verification

## 12. Image Verification
Deployment image:

```bash
kubectl get deployment krb-enterprise   -n uat-core   -o jsonpath='{.spec.template.spec.containers[?(@.name=="krb-enterprise")].image}'
```

Pod images:

```bash
kubectl get pods   -n uat-core   -l app=krb-enterprise   -o custom-columns='POD:.metadata.name,IMAGE:.spec.containers[0].image'
```

Container runtime images:

```bash
sudo ctr -n k8s.io images list
```

`crictl` is not installed.

## 13. Final Architecture

```text
GitHub
  ↓
GitHub Actions
  ↓
Build + Test
  ↓
Docker Build
  ↓
GHCR
  ↓
Self-hosted UAT Runner
  ↓
kubectl
  ↓
Kubernetes / uat-core
  ├── KRB Enterprise Deployment
  │      ↓
  │    Service
  │      ↓
  │    Ingress
  │
  └── PostgreSQL StatefulSet
         ↑
    independently managed
```

## 14. Final State
KRB Enterprise UAT deployment through GitHub Actions and Kubernetes works end-to-end.

Completed:
- Application build and tests
- Docker image creation
- GHCR push
- Self-hosted runner
- Kubernetes authentication
- Immutable SHA image deployment
- RollingUpdate
- Application startup
- Ingress verification
- Container image inspection

**Kubernetes deployment integration: COMPLETE**

## 15. Next Step
Kubernetes work is wrapped up. Continue to the next KRB Academy topic rather than adding unnecessary Kubernetes complexity.
