# KRB Academy

# GitHub & CI/CD — KRB Enterprise

**Session:** GitHub, GitHub Actions, GHCR & UAT Container Flow  
**Date:** 5 September 2026  
**Project Context:** KRB Enterprise  
**Status:** Session Complete

---

## 1. Session Objective

Build the first practical CI/CD pipeline for KRB Enterprise after completing the Docker learning track.

Covered:
- GitHub Actions CI
- Maven build and test automation
- Testcontainers with PostgreSQL 18
- Test-environment RSA JWT key configuration
- GitHub Actions action-version maintenance
- Docker image building inside CI
- Separate application and infrastructure repositories
- Multi-stage Docker builds in CI
- GitHub Container Registry (GHCR)
- GHCR image naming and tagging
- Pulling the CI-produced image onto the UAT Ubuntu VM
- Docker Compose consuming a registry image
- `latest` versus commit-SHA tags
- Foundation for automated UAT deployment

---

## 2. Repository Architecture

Application and infrastructure remain separate:

```text
~/workspace/
├── k_enterprise/
│   ├── Java / Spring Boot application
│   ├── pom.xml
│   ├── mvnw
│   ├── src/
│   └── .github/workflows/
│
└── k_enterprise_infra/
    ├── docker/
    │   ├── Dockerfile
    │   └── .dockerignore
    └── compose/
        └── compose.yaml
```

Principle:

> Application code and infrastructure configuration remain independently managed.

---

## 3. GitHub Actions CI

Workflow:

```text
k_enterprise/.github/workflows/ci.yaml
```

The pipeline performs:

```text
Push / Pull Request
        ↓
Checkout application
        ↓
Checkout infrastructure
        ↓
Java 25
        ↓
Maven clean verify
        ↓
Docker Buildx
        ↓
Docker image build
        ↓
GHCR push on main
```

Current action versions:

```yaml
actions/checkout@v5
actions/setup-java@v5
docker/setup-buildx-action@v3
docker/login-action@v3
docker/build-push-action@v6
```

The earlier Node.js 20 / `setup-java@v4` warnings were resolved by moving to the newer action versions.

---

## 4. CI Test Independence

The initial Spring Boot context test tried to use:

```text
jdbc:postgresql://localhost:5432/krb_enterprise
```

This failed in CI because the developer's local PostgreSQL instance is not available to the GitHub Actions runner.

The project already uses Testcontainers:

```java
new PostgreSQLContainer<>("postgres:18");
```

and:

```java
@DynamicPropertySource
static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
}
```

Therefore CI can create PostgreSQL itself:

```text
GitHub Actions runner
        ↓
Testcontainers
        ↓
PostgreSQL 18
        ↓
Spring Boot tests
```

No developer-local database is required.

---

## 5. RSA JWT Key Configuration for Tests

The original RSA configuration directly used:

```text
${user.home}/.krb-enterprise/secrets/private-key.pem
${user.home}/.krb-enterprise/secrets/public-key.pem
```

This caused CI test startup to fail because those deployment keys do not exist in the CI environment.

The key paths were made configurable through Spring properties while retaining the existing defaults.

This allows tests to use dedicated test keys while UAT/production continues to use its own mounted keys.

Important principle:

> Test keys and deployment keys are separate. Production/UAT private keys must not be placed in the application image or source repository.

---

## 6. Local CI Verification

After fixing Testcontainers and RSA test configuration:

```bash
./mvnw clean verify
```

completed successfully.

Result:

```text
Failures: 0
Errors: 0
BUILD SUCCESS
```

This established that the application can build and test independently of the developer's local PostgreSQL service.

---

## 7. Multi-Stage Docker Build

The infrastructure repository owns the Dockerfile.

The Docker build context is the application repository.

Current Dockerfile pattern:

```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /build/target/k_enterprise-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
```

The important distinction:

```text
Dockerfile location:
k_enterprise_infra/docker/Dockerfile

Build context:
k_enterprise/
```

This keeps the repositories separate while allowing the infrastructure repository to define the container build.

---

## 8. Two-Repository GitHub Actions Checkout

GitHub Actions checks out both repositories:

```yaml
- name: Checkout application
  uses: actions/checkout@v5
  with:
    path: k_enterprise

- name: Checkout infrastructure
  uses: actions/checkout@v5
  with:
    repository: kunalraj1705/k_enterprise_infra
    path: k_enterprise_infra
```

The runner therefore contains:

```text
GitHub Actions Runner
│
├── k_enterprise
│   └── application source
│
└── k_enterprise_infra
    └── Dockerfile
```

Docker then uses:

```yaml
context: k_enterprise
file: k_enterprise_infra/docker/Dockerfile
```

---

## 9. Local CI-Style Docker Build

A temporary workspace was used to reproduce the GitHub Actions repository layout:

```text
docker-ci-test/
├── k_enterprise/
└── k_enterprise_infra/
```

The build was:

```bash
docker build   -f k_enterprise_infra/docker/Dockerfile   -t krb-enterprise:ci   k_enterprise
```

The resulting image was approximately:

```text
169.8 MB
```

This confirmed that the multi-stage build worked with the same repository structure that CI would use.

---

## 10. Docker Build in GitHub Actions

Docker Buildx was added:

```yaml
- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v3
```

The image build uses:

```yaml
- name: Build and push Docker image
  uses: docker/build-push-action@v6
  with:
    context: k_enterprise
    file: k_enterprise_infra/docker/Dockerfile
```

The GitHub Actions Docker build succeeded.

---

## 11. GitHub Container Registry

GHCR was introduced as the container registry between CI and UAT.

Workflow permissions:

```yaml
permissions:
  contents: read
  packages: write
```

Authentication:

```yaml
- name: Log in to GHCR
  uses: docker/login-action@v3
  with:
    registry: ${{ env.REGISTRY }}
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}
```

Important principle:

> The built-in GitHub Actions `GITHUB_TOKEN` is used for publishing the package instead of creating another Docker password.

---

## 12. Final Image Name

The image repository name was deliberately kept short:

```text
k_enterprise
```

The fully qualified GHCR image is:

```text
ghcr.io/kunalraj1705/k_enterprise
```

The workflow uses:

```yaml
env:
  REGISTRY: ghcr.io
  IMAGE_NAME: kunalraj1705/k_enterprise
```

Published tags:

```text
ghcr.io/kunalraj1705/k_enterprise:latest
ghcr.io/kunalraj1705/k_enterprise:<github-sha>
```

---

## 13. `latest` and SHA Tags

Two tags are published.

### `latest`

```text
ghcr.io/kunalraj1705/k_enterprise:latest
```

Useful for:
- development
- quick manual testing
- referencing the current main build

### Commit SHA

```text
ghcr.io/kunalraj1705/k_enterprise:<github-sha>
```

Useful for:
- UAT
- production
- reproducible deployment
- rollback
- identifying exactly which source revision produced an image

The workflow only pushes images for normal pushes:

```yaml
push: ${{ github.event_name == 'push' }}
```

Therefore:

```text
Pull Request
    ↓
Build + Test + Docker Build
    ↓
No registry push

Push to main
    ↓
Build + Test + Docker Build
    ↓
Push to GHCR
```

---

## 14. GHCR Verification

The GHCR package was successfully published and became visible in GitHub Packages.

The Ubuntu UAT VM successfully pulled the image:

```bash
docker-compose pull krb-enterprise
```

Result:

```text
Image ghcr.io/kunalraj1705/k_enterprise:latest Pulled
```

The image size was inspected at approximately:

```text
169.8 MB
```

This matched the locally built image.

---

## 15. Docker Compose and `.env`

The Compose application service uses:

```yaml
image: ghcr.io/kunalraj1705/k_enterprise:${IMAGE_TAG}
```

The local `.env` contains:

```env
IMAGE_TAG=latest
```

Compose resolves:

```text
${IMAGE_TAG}
      ↓
   latest
      ↓
ghcr.io/kunalraj1705/k_enterprise:latest
```

Then:

```bash
docker-compose pull krb-enterprise
```

causes Docker to retrieve that image from GHCR.

---

## 16. CI and UAT Are Separate Systems

GitHub Actions and the UAT VM do not directly transfer the Docker image between each other.

GHCR is the bridge:

```text
             GitHub Actions
                    │
                    │ push image
                    ▼
                 ┌──────┐
                 │ GHCR │
                 └──┬───┘
                    │
                    │ pull image
                    ▼
                  UAT VM
```

CI:

```text
Git push
   ↓
GitHub Actions
   ↓
Tests
   ↓
Docker build
   ↓
GHCR
```

UAT:

```text
UAT VM
   ↓
docker compose pull
   ↓
GHCR
   ↓
Docker image
```

---

## 17. UAT Runtime Verification

The UAT VM successfully ran:

```text
ghcr.io/kunalraj1705/k_enterprise:latest
```

with:

```text
postgres:18
```

Docker Compose showed:

```text
KRB Enterprise    Up
PostgreSQL        Up (healthy)
```

The application was reachable on port:

```text
8282
```

The HTTP check returned:

```text
HTTP/1.1 401 Unauthorized
```

This was expected because Spring Security protects the endpoint.

The important result:

> The image built by CI, published to GHCR, pulled by UAT, and started successfully through Docker Compose.

---

## 18. Current CI/CD Architecture

```text
Developer
   │
   │ git push main
   ▼
GitHub
   │
   ▼
GitHub Actions
   │
   ├── Checkout k_enterprise
   ├── Checkout k_enterprise_infra
   ├── Java 25
   ├── Maven clean verify
   ├── Testcontainers / PostgreSQL 18
   ├── Docker Buildx
   └── Build + push image
              │
              ▼
             GHCR
              │
              ▼
        Ubuntu UAT VM
              │
        Docker Compose
              │
        ┌─────┴─────┐
        ▼           ▼
KRB Enterprise   PostgreSQL
   :8282           :5432
```

---

## 19. Current Status

```text
GitHub repository                         ✅
GitHub Actions CI                         ✅
Java 25                                   ✅
Maven build                               ✅
Automated tests                           ✅
Testcontainers PostgreSQL 18              ✅
RSA test-key configuration                ✅
checkout@v5                               ✅
setup-java@v5                             ✅
Docker Buildx                             ✅
Multi-stage Docker build                  ✅
Separate application/infra repositories   ✅
GHCR publishing                           ✅
Short image name                          ✅
latest tag                                ✅
SHA tag                                   ✅
GHCR pull on UAT VM                       ✅
Compose using GHCR image                  ✅
UAT runtime verification                  ✅
```

---

## 20. Remaining Work — Automated UAT Deployment

Current process:

```text
GitHub Actions
      ↓
GHCR
      ↓
Manual:
docker compose pull
docker compose up -d
```

Target:

```text
GitHub Actions
      ↓
GHCR
      ↓
Automated UAT deployment
      ↓
Pull exact SHA image
      ↓
docker compose up -d
      ↓
Health verification
```

The next session should cover:

```text
├── UAT deployment authentication
├── GitHub Actions secrets
├── SSH deployment
├── SHA-based image deployment
├── Docker Compose deployment
├── Deployment verification
└── Rollback strategy
```

---

## Key Takeaways

1. CI must not depend on a developer's local database.
2. Testcontainers provides isolated PostgreSQL for integration tests.
3. Test-specific RSA keys/configuration keep CI independent from deployment secrets.
4. Application and infrastructure repositories can remain separate.
5. Dockerfile location and Docker build context are independent concepts.
6. Multi-stage builds keep Maven out of the final runtime image.
7. GHCR provides the bridge between CI and deployment.
8. `latest` is convenient; SHA tags provide reproducible deployment identity.
9. Compose can resolve `${IMAGE_TAG}` from `.env`.
10. The UAT VM can pull the exact image produced by CI from GHCR.
11. Secrets and JWT private keys should remain outside the Docker image and source repository.
12. The next step is automated UAT deployment.

---

## Session Completion

**GitHub & CI/CD — 5 September 2026**

**Status: COMPLETED**

KRB Enterprise now has a functioning CI pipeline that builds, tests, containerizes, and publishes the application image to GHCR. The UAT VM has successfully pulled and run the registry-produced image through Docker Compose.

The remaining CI/CD task is automated UAT deployment.

---

# KRB Academy Progress

```text
Docker
└── COMPLETED ✅

GitHub & CI/CD
├── GitHub repository                         ✅
├── GitHub Actions                            ✅
├── Maven CI                                  ✅
├── Testcontainers                            ✅
├── Docker build in CI                        ✅
├── Multi-repository checkout                 ✅
├── GHCR                                      ✅
├── Image tagging                             ✅
├── Registry-based Compose deployment         ✅
└── Automated UAT deployment                  Next
```

---


**End of KRB Academy Session — 5 September 2026**
