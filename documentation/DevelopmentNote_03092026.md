# KRB Enterprise — Docker Containerization & Runtime Verification

## Session Summary

Today we containerized the KRB Enterprise Spring Boot application and started it together with PostgreSQL using Docker Compose.

No new Docker Academy topic was introduced today. The session focused on applying previously learned Docker concepts to the KRB Enterprise application and verifying that the containerized system starts and connects correctly.

## 1. Spring Boot Application Build

The KRB Enterprise application was built successfully using the Maven wrapper:

```bash
./mvnw clean package -DskipTests
```

The resulting application JAR was:

```text
target/k_enterprise-0.0.1-SNAPSHOT.jar
```

The JAR was then prepared as the application artifact for the Docker image build.

## 2. KRB Enterprise Docker Image

A Dockerfile was created in the separate infrastructure repository.

Current Dockerfile:

```dockerfile
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY k_enterprise.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
```

The image was built as:

```bash
docker build -f docker/Dockerfile -t krb-enterprise:0.0.1 docker/build
```

Image:

```text
krb-enterprise:0.0.1
```

The application is therefore packaged as a runnable Docker image using the Java 25 JRE base image.

## 3. Infrastructure Repository

Docker-related files are maintained separately from the application repository.

Structure:

```text
~/workspace/
├── k_enterprise/
│   ├── pom.xml
│   ├── mvnw
│   ├── src/
│   ├── .gitignore
│   └── documentation/
│
└── k_enterprise_infra/
    ├── docker/
    │   ├── Dockerfile
    │   └── .dockerignore
    └── compose/
        └── compose.yaml
```

The infrastructure repository is connected to GitHub using SSH.

## 4. Docker Compose Application Service

The KRB Enterprise application was added as a Compose service.

Relevant configuration:

```yaml
krb-enterprise:
  image: krb-enterprise:0.0.1
  ports:
    - "8282:8282"
  environment:
    SPRING_PROFILES_ACTIVE: uat
    DB_URL: jdbc:postgresql://postgres:5432/krb_enterprise
    DB_USERNAME: krb
    DB_PASSWORD: password
```

The important runtime distinction is:

```text
Application container
        ↓
postgres:5432
        ↓
PostgreSQL container
```

The application does not use `localhost` to reach PostgreSQL inside Compose. It uses the Compose service name:

```text
postgres
```

## 5. UAT Runtime Configuration

The container runs with the UAT Spring profile:

```text
SPRING_PROFILES_ACTIVE=uat
```

The UAT datasource configuration resolves its values from runtime environment variables:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

Runtime values supplied by Compose:

```text
DB_URL=jdbc:postgresql://postgres:5432/krb_enterprise
DB_USERNAME=krb
DB_PASSWORD=password
```

This confirms that the same application artifact can receive environment-specific configuration at runtime.

## 6. PostgreSQL Service

PostgreSQL continues to run as a separate Compose service:

```yaml
postgres:
  image: postgres:18
```

The database uses the persistent named volume:

```yaml
volumes:
  - postgres_data:/var/lib/postgresql
```

The PostgreSQL service also has a health check:

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U krb -d krb_enterprise"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 10s
```

## 7. Application Startup Dependency

The application waits for PostgreSQL to become healthy before starting:

```yaml
depends_on:
  postgres:
    condition: service_healthy
```

Therefore the startup flow is:

```text
PostgreSQL container starts
        ↓
Health check executes
        ↓
PostgreSQL becomes HEALTHY
        ↓
KRB Enterprise container starts
        ↓
Spring Boot connects to PostgreSQL
```

This uses Docker Compose concepts that were already covered in the Docker Academy sessions.

## 8. JWT Key Mounting

The KRB Enterprise application requires JWT signing keys.

The keys were kept outside the Docker image and mounted into the container:

```yaml
volumes:
  - /home/ubuntu/workspace/k_enterprise/secrets/private-key.pem:/root/.krb-enterprise/secrets/private-key.pem:ro
  - /home/ubuntu/workspace/k_enterprise/secrets/public-key.pem:/root/.krb-enterprise/secrets/public-key.pem:ro
```

The application expects the keys under:

```text
/root/.krb-enterprise/secrets/
```

The mounts are read-only:

```text
:ro
```

The keys were not copied into the Docker image.

## 9. Runtime Verification

Docker Compose was started successfully.

The running services were verified:

```text
krb-enterprise:0.0.1   Up
postgres:18            Up (healthy)
```

The Spring Boot logs confirmed:

```text
Spring Boot 4.1.1
Java 25.0.4
UAT profile
Tomcat port 8282
```

The application successfully connected to:

```text
jdbc:postgresql://postgres:5432/krb_enterprise
```

Flyway confirmed:

```text
3 migrations
Current schema version: 3
Database is up to date
```

Hibernate/JPA initialized successfully.

The final startup message confirmed:

```text
Started KEnterpriseApplication
```

## 10. HTTP Verification

The application was tested from the Ubuntu host:

```bash
curl -i http://localhost:8282
```

The response was:

```text
HTTP/1.1 401
```

with:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication is required to access this resource."
}
```

This confirmed that:

```text
Host
 ↓
Port 8282
 ↓
KRB Enterprise container
 ↓
Spring Boot
 ↓
Spring Security
```

was reachable and processing the request.

The `401 Unauthorized` response is expected for a protected endpoint without valid authentication.

## 11. Database Verification

The PostgreSQL database was also checked directly.

The expected application tables were present, including:

```text
users
```

The database was freshly initialized and:

```sql
SELECT COUNT(*) FROM users;
```

returned:

```text
0
```

This was expected because no users had been registered in the fresh database.

## 12. What Was Learned Today

Today's session was primarily an implementation and verification session rather than a new Docker theory session.

Previously learned concepts were applied:

```text
Dockerfile
    ↓
Docker Image
    ↓
Docker Compose
    ↓
Application Container
    ↓
PostgreSQL Container
    ↓
Compose Network
    ↓
Health Check
    ↓
depends_on
    ↓
Runtime Configuration
    ↓
Persistent PostgreSQL Storage
```

No new Docker Academy topic was added to the completed-topic list today.

## Current Status

```text
KRB Enterprise
    ↓
Spring Boot JAR built
    ↓
Docker image created
    ↓
Compose application service configured
    ↓
PostgreSQL service configured
    ↓
UAT runtime configuration supplied
    ↓
JWT keys mounted
    ↓
PostgreSQL healthy
    ↓
Application started
    ↓
Flyway validated
    ↓
JPA initialized
    ↓
HTTP reachability verified
    ↓
Database verified
```

## Next Session

The next Docker Academy topic is:

**Compose Environment & Secrets**

After that:

```text
Compose Environment/Secrets
        ↓
CI/CD Integration
        ↓
Multi-stage Builds
        ↓
Runtime Dependency Recovery
        ↓
KRB Enterprise Dockerization
```

Runtime Dependency Recovery has intentionally been moved to a later stage so that it can be studied after environment/secrets and CI/CD are established.

## KRB Enterprise Development Status

```text
Environment Configuration      ✅ Completed
Docker Containerization        ✅ Implemented
Docker Compose Runtime         ✅ Implemented
Application Verification       ✅ Completed
Database Verification          ✅ Completed

Next:
Compose Environment & Secrets
```

---

**End of Development Notes**
