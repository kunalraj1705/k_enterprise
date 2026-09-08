# KRB Enterprise — Development Notes
## 2026-09-07

## Session Objective

Continue KRB Enterprise development after Dockerization and the initial GitHub Actions CI/CD pipeline.

Today's work focused on:
- UAT deployment health verification
- Spring Boot Actuator
- Local Testcontainers/PostgreSQL testing
- Removing Docker as a local development prerequisite
- Test-suite cleanup
- Understanding Flyway

---

## 1. UAT Deployment Health Verification

The current UAT deployment verifies that Docker Compose services are running:

```bash
docker-compose -f compose.yaml ps
```

This confirms container state but does not prove that Spring Boot is ready to serve requests.

The desired deployment verification is:

```text
Container running
      ↓
Spring Boot started
      ↓
GET /actuator/health
      ↓
HTTP 200 + UP
      ↓
Deployment SUCCESS
```

A retry-based health-check loop was designed using:

```bash
curl -fsS http://localhost:8282/actuator/health
```

If the application does not become healthy, the deployment should fail and application logs should be captured.

This automated UAT health verification was identified as the next CD implementation step and was not completed today.

---

## 2. Spring Boot Actuator

Spring Boot Actuator was added:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Health exposure:

```properties
management.endpoints.web.exposure.include=health
```

The security configuration permits `/actuator/health` so deployment verification can call it without normal application authentication.

Target endpoint:

```text
GET /actuator/health
```

Expected healthy response:

```json
{"status":"UP"}
```

Actuator is therefore the application-level health signal for UAT deployment verification.

---

## 3. Local Testcontainers Problem

The Windows development environment does not have Docker installed.

The existing PostgreSQL integration tests used Testcontainers and therefore attempted to start PostgreSQL containers.

The failure was:

```text
Could not find a valid Docker environment
```

and:

```text
Previous attempts to find a Docker environment failed
```

The Docker-dependent tests were:

```text
KEnterpriseApplicationTests
PostgresIntegrationTest
PostgresSpringBootIntegrationTest
```

Non-Docker tests continued to pass, including:

```text
RegisterUserTest
UserTest
SpringSecurityPasswordHasherTest
```

---

## 4. Decision: Remove PostgreSQL/Testcontainers Integration Tests

A project-level decision was made not to require Docker on every developer's local machine.

The requirement is that a fresh checkout should support:

```bash
./mvnw clean verify
```

without requiring:
- Docker
- PostgreSQL installed locally
- special Maven skip parameters

Therefore these tests were removed:

```text
KEnterpriseApplicationTests
PostgresIntegrationTest
PostgresSpringBootIntegrationTest
```

### Reasoning

`PostgresIntegrationTest` specifically tested a Testcontainers PostgreSQL connection.

`PostgresSpringBootIntegrationTest` started Spring Boot against Testcontainers PostgreSQL.

`KEnterpriseApplicationTests` also used Testcontainers PostgreSQL. After the Testcontainers setup was removed, a plain `@SpringBootTest` still attempted to load the complete application context, including DataSource and Flyway, and failed because no local datasource configuration was available.

The tests were therefore removed rather than requiring developers to repeatedly use test-exclusion flags.

---

## 5. Local Test Strategy

The intended repository behavior is:

```text
Fresh checkout
      ↓
./mvnw clean verify
      ↓
Non-Docker tests
      ↓
BUILD SUCCESS
```

Current meaningful non-Docker tests include:

```text
RegisterUserTest
UserTest
SpringSecurityPasswordHasherTest
```

The PostgreSQL integration tests were useful for validating real database integration, but they were not retained as a mandatory local prerequisite.

---

## 6. Testcontainers Dependency Cleanup

After removing the Testcontainers tests, these test dependencies are no longer expected to be required if no remaining source/test code references Testcontainers:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

These should be removed after confirming no remaining usage.

Do **not** remove the actual PostgreSQL/Flyway application dependencies:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>

<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

---

## 7. Flyway

Flyway was reviewed to clarify its role.

```text
PostgreSQL = Database
Flyway     = Database schema migration/versioning
Hibernate  = ORM
```

Flyway manages versioned database schema changes.

Migrations are stored under:

```text
src/main/resources/db/migration/
```

Example:

```text
V1__create_users_table.sql
V2__add_user_status.sql
V3__add_user_email.sql
```

Flyway executes pending migrations in version order and records migration history.

KRB Enterprise deployment therefore follows:

```text
New application version
        ↓
Spring Boot starts
        ↓
Flyway checks migration history
        ↓
Pending migrations execute
        ↓
PostgreSQL reaches expected schema
        ↓
Application continues startup
```

Flyway remains an important part of the application because database schema changes need to be versioned and reproducible across environments.

---

## 8. Current Architecture Decision

The project now clearly separates local development, CI, and UAT responsibilities.

### Local development

```text
Java + Maven
    ↓
Unit/application tests
    ↓
No Docker requirement
```

### CI

```text
GitHub Actions
    ↓
Build
    ↓
Test
    ↓
Docker image
    ↓
GHCR
```

### UAT

```text
GitHub Actions
    ↓
Pull immutable SHA image
    ↓
Docker Compose
    ↓
PostgreSQL
    +
KRB Enterprise
    ↓
Actuator health verification
```

This keeps local development lightweight while retaining containerized deployment infrastructure.

---

## 9. Work Remaining

Next session:

1. Remove unused Testcontainers dependencies from `pom.xml`.
2. Run:
   ```bash
   ./mvnw clean verify
   ```
3. Confirm a clean local build succeeds without Docker.
4. Commit the test/dependency cleanup.
5. Implement automated UAT `/actuator/health` verification in GitHub Actions.
6. Test the successful deployment path.
7. Test failure handling.
8. Design rollback behavior.

---

## Session Status

### Completed

- Actuator dependency/configuration added.
- `/actuator/health` prepared for deployment verification.
- Health endpoint security access configured.
- Local Docker/Testcontainers failure identified.
- Decision made not to install Docker on Windows.
- PostgreSQL/Testcontainers integration tests removed.
- Docker-dependent `KEnterpriseApplicationTests` removed.
- Local test strategy established.
- Flyway role clarified.

### Not completed

- Final Testcontainers dependency cleanup.
- Final clean Maven verification after dependency cleanup.
- Automated UAT health verification.
- Deployment failure handling.
- Rollback.

**Next starting point: clean `pom.xml` → local Maven verification → UAT health verification.**
