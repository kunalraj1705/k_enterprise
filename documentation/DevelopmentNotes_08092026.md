# KRB Enterprise Development Notes — 2026-09-08

## Session Summary
Completed GitHub Actions CI/CD work for KRB Enterprise and finalized the transition toward Kubernetes.

## Development Work Completed

### Maven and Application
- Removed the no-longer-required Testcontainers integration tests/dependencies.
- Ran `./mvnw clean verify`.
- Result: 11 tests passed, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.
- Changed Maven `finalName` to `krbenterprise`.
- Updated Docker/deployment references to the generated `krbenterprise.jar`.

### Spring Security
Consolidated authorization into one `authorizeHttpRequests` block.
Public:
- `POST /api/v1/user/customer`
- `POST /api/v1/auth/login`
- `GET /actuator/health`
All other requests require authentication.
JWT resource-server configuration remains enabled.

### Application Context Path
Added:
`server.servlet.context-path=/krbenterprise`

Health endpoint:
`http://localhost:8282/krbenterprise/actuator/health`

Security matchers remain without `/krbenterprise` because the servlet context path is separate from application request mappings.

### UAT Verification
Verified on Ubuntu UAT:
- HTTP 200 from `/krbenterprise/actuator/health`
- Health status `UP`
- Liveness and readiness groups available
- Running application image identified by exact Git SHA

PostgreSQL was healthy and the Spring Boot application was running successfully in Docker.

### GitHub Actions CI/CD
The workflow now performs:
- Application and infrastructure checkout
- Java 25 setup
- Maven verification
- Docker Buildx
- GHCR authentication
- Docker build and SHA-based publishing
- UAT deployment through the self-hosted runner
- UAT variables/secrets
- Post-deployment health verification

The deployment health check waits for startup, checks `/krbenterprise/actuator/health`, and prints application logs if health verification fails.

### Naming
Current intended names:
- Maven artifact: `krbenterprise.jar`
- GHCR image: `ghcr.io/kunalraj1705/krbenterprise:<SHA>`
- UAT local image: `krbenterprise:<SHA>`
- Container: `krb-enterprise`

### Kubernetes Transition Decision
Rolling/blue-green deployment was discussed. The decision was to implement this properly in Kubernetes rather than creating a custom multi-container replacement mechanism in Docker Compose.

Kubernetes will provide:
- Multiple Pods using the same container port
- Individual Pod IPs
- A stable Service endpoint
- Deployment-managed rolling updates
- Readiness-based traffic eligibility
- Controlled rollout and rollback

## Current State
GitHub Actions CI/CD for the current Docker/Compose UAT architecture is complete.

Next implementation track: **Kubernetes**.
