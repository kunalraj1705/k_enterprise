# KRB Enterprise — GitHub Actions UAT Deployment Notes

**Date:** 7 September 2026  
**Scope:** Everything completed and verified today while preparing GitHub Actions CD to UAT.

## 1. Current CI/CD Architecture

```text
GitHub Repository
      ↓
GitHub Actions CI
      ↓
Docker Buildx
      ↓
GHCR
      ↓
Self-hosted UAT Runner
      ↓
Docker Compose
      ↓
UAT Application + PostgreSQL
```

Application repository:

```text
k_enterprise
```

Infrastructure repository:

```text
k_enterprise_infra
```

Container image:

```text
ghcr.io/kunalraj1705/k_enterprise
```

CI publishes both:

```text
latest
<commit SHA>
```

The SHA-tagged image used during today's verification was:

```text
ghcr.io/kunalraj1705/k_enterprise:906d5f28ee7014caa0fc1fe2444a3db87da978
```

## 2. CI Status Before UAT Deployment

Already working:

- Java 25
- Maven build and tests
- Testcontainers PostgreSQL 18
- Docker Buildx
- Multi-stage Docker build
- GHCR authentication
- GHCR publishing
- `latest` and SHA tags
- PR builds without publishing
- main branch builds and publishing

## 3. UAT Docker Environment

Docker is installed through Snap:

```text
docker 29.6.1
```

Available Compose command:

```bash
docker-compose
```

Version:

```text
Docker Compose v5.3.1
```

The following does not work on this UAT host:

```bash
docker compose
```

The working executable is:

```text
/snap/bin/docker-compose
```

## 4. Self-Hosted Runner

Repository:

```text
kunalraj1705/k_enterprise
```

Labels:

```text
self-hosted
linux
x64
uat
```

Runner user:

```text
github-runner
```

Runner installation:

```text
/opt/actions-runner
```

The user belongs to the Docker group:

```text
github-runner docker
```

The runner was registered successfully and a test GitHub Actions job completed successfully.

The runner was started manually using:

```bash
cd /opt/actions-runner
./run.sh
```

It is not installed as a systemd service.

The runner was already stopped before today's final troubleshooting.

## 5. GitHub Actions Environment

The deployment job targets:

```yaml
environment: UAT
```

and the self-hosted runner:

```yaml
runs-on: [self-hosted, linux, x64, uat]
```

The purpose of the UAT Environment is to separate deployment logic from environment-specific configuration, secrets, and protection.

## 6. UAT Compose File

Infrastructure Compose file:

```text
k_enterprise_infra/compose/compose.yaml
```

It contains:

- PostgreSQL 18
- KRB Enterprise application
- PostgreSQL healthcheck
- `depends_on` with `condition: service_healthy`
- named PostgreSQL volume
- application port `8282`
- environment-variable substitution
- read-only JWT key mounts

Application image:

```yaml
image: ghcr.io/kunalraj1705/k_enterprise:${IMAGE_TAG}
```

Required application variables:

```text
IMAGE_TAG
SPRING_PROFILES_ACTIVE
DB_URL
DB_USERNAME
DB_PASSWORD
```

## 7. GHCR Verification

UAT successfully pulled the image from GHCR.

Therefore the registry path is working:

```text
GitHub Actions → GHCR → UAT
```

GHCR access is not the current blocker.

## 8. Runner Workspace

GitHub Actions workspace:

```text
/opt/actions-runner/_work/k_enterprise/k_enterprise
```

Infrastructure checkout:

```text
/opt/actions-runner/_work/k_enterprise/k_enterprise/k_enterprise_infra
```

Compose file:

```text
/opt/actions-runner/_work/k_enterprise/k_enterprise/k_enterprise_infra/compose/compose.yaml
```

## 9. Deployment Failure

This command fails:

```bash
docker-compose   -f /opt/actions-runner/_work/k_enterprise/k_enterprise/k_enterprise_infra/compose/compose.yaml   config
```

Error:

```text
open /opt/actions-runner/_work/k_enterprise/k_enterprise/k_enterprise_infra/compose/compose.yaml: no such file or directory
```

However, the file definitely exists.

The `github-runner` user can successfully run:

```bash
cat /opt/actions-runner/_work/k_enterprise/k_enterprise/k_enterprise_infra/compose/compose.yaml
```

## 10. Linux Permissions Verification

File ownership:

```text
github-runner github-runner
```

Permissions:

```text
-rw-r--r--
```

The runner directory was also given explicit ownership/permissions:

```bash
sudo chown -R github-runner:github-runner /opt/actions-runner
sudo chmod -R u+rwX /opt/actions-runner
```

The Compose failure remained.

Therefore normal Linux filesystem permissions are not the problem.

## 11. Snap Confinement Verification

Docker Snap connections were inspected.

Docker has a `home` interface and other interfaces, but no usable `system-files` plug for granting arbitrary `/opt` access.

The following was connected:

```bash
sudo snap connect docker:removable-media
```

Verification:

```text
removable-media docker:removable-media :removable-media manual
```

The Compose failure against `/opt/actions-runner` remained.

## 12. Definitive Proof

The same Compose file was copied to:

```text
/home/github-runner/compose-test/compose.yaml
```

This command succeeded:

```bash
docker-compose -f ~/compose-test/compose.yaml config
```

It produced the resolved Compose configuration.

It only warned that variables were unset because this was an isolated test.

Therefore:

```text
Shell user can access /opt
        +
docker-compose cannot access /opt
        +
docker-compose can access /home/github-runner
        =
Docker Snap confinement
```

This is the confirmed root cause.

## 13. What Was Ruled Out

Not the problem:

- GitHub Actions
- runner registration
- runner user permissions
- Docker group membership
- GHCR access
- missing Compose file
- normal Unix ownership
- normal Unix read permissions

Confirmed problem:

```text
Docker Snap filesystem confinement
```

## 14. Current Fastest Workaround

Do not move the runner installation.

Keep:

```text
/opt/actions-runner
```

Copy the checked-out Compose file to a Snap-accessible home directory:

```text
/home/github-runner/deploy/compose.yaml
```

Then run Compose from there.

Deployment step:

```yaml
- name: Deploy application
  run: |
    export IMAGE_TAG=${{ github.sha }}

    DEPLOY_DIR="/home/github-runner/deploy"
    mkdir -p "$DEPLOY_DIR"

    cp "$GITHUB_WORKSPACE/k_enterprise_infra/compose/compose.yaml"        "$DEPLOY_DIR/compose.yaml"

    cd "$DEPLOY_DIR"

    docker-compose -f compose.yaml pull krb-enterprise
    docker-compose -f compose.yaml up -d krb-enterprise
```

## 15. Remaining Deployment Work

1. Finalize the UAT deployment workflow.
2. Supply UAT values for:
   - `SPRING_PROFILES_ACTIVE`
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`
3. Deploy the immutable SHA-tagged image.
4. Verify Compose services.
5. Verify application reachability.
6. Add deployment verification/rollback handling.

## 16. Important Lesson

A filesystem error from a sandboxed tool can be misleading.

The distinction is:

```text
Linux permissions
        ≠
application sandbox permissions
```

Always test the actual tool against the path, not only the shell user's access.

## 17. Status

```text
CI                                      ✅
GHCR publishing                         ✅
UAT GHCR pull                           ✅
Self-hosted runner                      ✅
Runner Docker access                    ✅
Runner Docker Compose access            ✅
Linux filesystem permissions            ✅
Snap confinement identified             ✅
Automated UAT deployment                ⏳
UAT environment variables               ⏳
Deployment verification                 ⏳
```

# End of Notes
