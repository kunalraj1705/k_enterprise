# KRB Enterprise — Development & Learning Notes

## Purpose

This is the running engineering journal for KRB Enterprise.

For every major step, record:
- What we built
- Why we built it
- Architecture decisions
- Commands used
- Errors encountered
- Root cause
- Resolution
- Lessons learned
- Current checkpoint and next step

---

# 1. Project Setup

- Project: `KRB Enterprise`
- IDE: VS Code
- Host: Windows 11
- Java: JDK 25.0.3
- Spring Boot: 4.1.0
- Maven Wrapper
- Application port: `8282`
- Database: PostgreSQL 18.4
- PostgreSQL runs in Ubuntu inside VirtualBox
- Docker runs inside Ubuntu
- PostgreSQL container: `krb-postgres`
- Database: `krb_enterprise`
- DB user: `krb`
- Development DB password was changed to `lolnocreds` during local setup. Do not commit credentials to source control.

Initial application startup succeeded with Java 25 and Spring Boot 4.1.0.

---

# 2. User Registration Architecture

The first major feature is customer/user registration.

Target flow:

    HTTP
      ↓
    UserController
      ↓
    RegisterUser
      ↓
    UserRepository
      ↓
    Persistence implementation

The application/domain layers should not depend directly on PostgreSQL or JPA.

---

# 3. Domain User

The current domain `User` contains:

- `UUID id`
- `String email`
- `String passwordHash`
- `UserRole role`
- `UserStatus status`
- `Instant createdAt`
- `Instant updatedAt`

`User.create(email, passwordHash)` creates:

- random UUID
- role = `CUSTOMER`
- status = `ACTIVE`
- current timestamp for created/updated

Domain behavior includes:

- `suspend()`
- `activate()`

The domain intentionally contains no JPA/Spring annotations.

---

# 4. UserRepository Abstraction

Current contract:

```java
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID userId);

    boolean existsByEmail(String email);
}
```

The application depends on this abstraction rather than PostgreSQL.

---

# 5. RegisterUser

Current application service:

```java
@Service
public class RegisterUser {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public RegisterUser(
            UserRepository userRepository,
            PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public User execute(String email, String password) {

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists.");
        }

        String passwordHash = passwordHasher.hash(password);
        User user = User.create(email, passwordHash);

        return userRepository.save(user);
    }
}
```

Important security flow:

    Raw password
        ↓
    PasswordHasher
        ↓
    passwordHash
        ↓
    persistence

Plaintext passwords are never stored.

---

# 6. Spring Security Issues

Spring Security initially protected the API and caused `401 Unauthorized`.

Registration was explicitly permitted:

```java
http.authorizeHttpRequests(auth ->
    auth.requestMatchers(HttpMethod.POST, "/api/v1/user")
        .permitAll()
        .anyRequest().authenticated()
);
```

A `403 Forbidden` was then encountered because POST requests were affected by CSRF protection.

During development, the registration endpoint was configured for unauthenticated access. The final security model will be refined later.

Spring Boot also generated a development security password and printed it to the console. That generated password is only for development and should not be treated as production authentication.

---

# 7. Initial In-Memory Repository

An in-memory `UserRepository` implementation was used initially so the domain/application layers could be developed without a database.

This was intentionally temporary.

Once PostgreSQL persistence was implemented, the in-memory implementation was removed from the application context.

---

# 8. PostgreSQL + Docker

Docker version verified:

```text
Docker 29.1.3
```

Docker works without `sudo`.

PostgreSQL container:

```text
Name: krb-postgres
Image: postgres:18
Port: 5432
```

The Windows application connects through the VM networking/port-forwarding setup.

Connection:

```bash
docker exec -it krb-postgres psql -U krb -d krb_enterprise
```

---

# 9. JPA Configuration

Current database configuration:

```properties
spring.application.name=k_enterprise
server.port=8282

spring.datasource.url=jdbc:postgresql://localhost:5432/krb_enterprise
spring.datasource.username=krb
spring.datasource.password=password

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Kolkata
```

`ddl-auto=validate` is intentional.

Responsibilities:

    Flyway
      ↓
    Owns schema creation/evolution

    Hibernate
      ↓
    Validates entity ↔ database schema

Hibernate should not silently modify the production-style schema.

---

# 10. Flyway Problem #1 — Migration Did Not Run

Initially, Flyway dependencies were present:

```text
org.flywaydb:flyway-core:12.4.0
org.flywaydb:flyway-database-postgresql:12.4.0
```

The migration file existed at:

```text
src/main/resources/db/migration/V1__create_users_table.sql
```

It was also copied to:

```text
target/classes/db/migration/
```

But PostgreSQL had no tables and no `flyway_schema_history`.

Hibernate failed with:

```text
Schema validation: missing table [users]
```

Root cause:

Spring Boot's Flyway integration/auto-configuration was not being activated correctly.

Resolution:

Use Spring Boot's Flyway starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
```

and keep the PostgreSQL-specific Flyway module:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

After this, Flyway executed correctly.

---

# 11. Flyway V1

Migration:

```text
V1__create_users_table.sql
```

SQL:

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,

    CONSTRAINT uk_users_email UNIQUE (email)
);
```

Flyway created:

```text
flyway_schema_history
users
```

---

# 12. Domain/Database Mismatch

The domain already contained:

- `createdAt`
- `updatedAt`

but V1 did not contain those columns.

We did not edit V1 because it had already been applied.

Instead, we created V2.

This reinforces the migration rule:

> Once a migration has been applied, create a new migration for a new schema change.

---

# 13. Flyway V2

Migration:

```text
V2__add_user_timestamps.sql
```

SQL:

```sql
ALTER TABLE users
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL;
```

The domain uses Java `Instant`, representing an absolute point in time.

Flyway then reported:

```text
Successfully validated 2 migrations
Current version of schema "public": 2
Schema "public" is up to date.
```

---

# 14. Timezone Problem

Flyway later failed with:

```text
FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"
```

PostgreSQL accepted:

```text
Asia/Kolkata
```

but the Java connection startup was sending:

```text
Asia/Calcutta
```

The Windows host timezone was:

```text
India Standard Time
```

The JVM therefore needed an explicit runtime timezone standard.

---

# 15. Timezone Resolution for Spring Boot

Running Spring Boot with an explicit UTC JVM setting worked:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Duser.timezone=UTC"
```

An earlier command was incorrectly escaped and produced:

```text
Unknown lifecycle phase ".run.jvmArguments=-Duser.timezone=UTC"
```

PowerShell does not use backslash as the normal escape character for `:`.

Correct Maven syntax is:

```text
spring-boot:run
```

not:

```text
spring-boot\:run
```

---

# 16. Timezone Resolution for Maven Tests

`mvn test` uses the Surefire test JVM, which is separate from the Spring Boot run JVM.

The tests therefore still encountered the `Asia/Calcutta` problem.

Resolution:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>-Duser.timezone=UTC</argLine>
    </configuration>
</plugin>
```

Then:

```powershell
.\mvnw.cmd clean test
```

passed.

Lesson:

    Spring Boot JVM
    ≠
    Surefire test JVM

A JVM setting applied to one does not automatically configure the other.

---

# 17. UserEntity

Persistence is intentionally separate from the domain.

`UserEntity` maps:

```text
id             → UUID
email          → VARCHAR(320)
passwordHash   → password_hash VARCHAR(255)
status         → VARCHAR(20)
role           → VARCHAR(20)
createdAt      → created_at
updatedAt      → updated_at
```

The entity uses:

```java
@Entity
@Table(name = "users")
```

The domain `User` remains free from JPA annotations.

---

# 18. Persistence Repository Architecture

Final persistence boundary:

    RegisterUser
         ↓
    UserRepository
         ↓
    PostgresUserRepository
         ↓
    UserEntityMapper
         ↓
    UserJpaRepository
         ↓
    Hibernate
         ↓
    PostgreSQL

Application knows:

```text
UserRepository
```

Infrastructure knows:

```text
PostgresUserRepository
UserJpaRepository
UserEntity
```

---

# 19. UserJpaRepository

Current Spring Data interface:

```java
public interface UserJpaRepository
        extends JpaRepository<UserEntity, UUID> {

    boolean existsByEmail(String email);
}
```

Spring Data derives the query from the method name.

---

# 20. UserEntityMapper

The mapper is responsible for:

```text
Domain User
    ↕
UserEntity
```

Role/status conversion:

```java
user.getStatus().name()
user.getRole().name()
```

and:

```java
UserStatus.valueOf(entity.getStatus())
UserRole.valueOf(entity.getRole())
```

This keeps persistence details outside the domain.

---

# 21. PostgresUserRepository

Responsibilities:

### Save

```text
User
 ↓
UserEntityMapper
 ↓
UserEntity
 ↓
UserJpaRepository.save()
 ↓
PostgreSQL
```

### Find by ID

```text
UserJpaRepository.findById()
 ↓
UserEntity
 ↓
UserEntityMapper.toDomain()
 ↓
User
```

### Check email

```text
UserJpaRepository.existsByEmail()
```

---

# 22. Duplicate Repository Bean Error

After PostgreSQL persistence was added, Spring failed:

```text
No qualifying bean of type UserRepository available:
expected single matching bean but found 2
```

Beans:

```text
inMemoryUserRepository
postgresUserRepository
```

Root cause:

Two Spring beans implemented the same `UserRepository`.

Resolution:

Delete the temporary `InMemoryUserRepository`.

Do NOT solve this by coupling `RegisterUser` to `PostgresUserRepository`.

We deliberately did not use `@Primary` because the in-memory implementation was no longer required in the production application context.

---

# 23. Successful Persistence Startup

After removing the in-memory repository:

```text
Found 1 JPA repository interface.
```

Flyway:

```text
Successfully validated 2 migrations
Schema "public" is up to date.
```

Hibernate initialized successfully.

Application started on port `8282`.

---

# 24. Real API → PostgreSQL Verification

Registration through:

```text
POST /api/v1/user
```

produced Hibernate SQL including:

```text
select ... from users where email=?
```

and:

```text
insert into users (...)
```

This proved the real application path reached PostgreSQL.

The database row contained:

```text
status = ACTIVE
role = CUSTOMER
```

and `created_at` / `updated_at` values.

The password was stored as a bcrypt hash, not plaintext.

---

# 25. PostgreSQL Persistence Verification

We stopped Spring Boot.

Then:

```bash
docker stop krb-postgres
```

and:

```bash
docker start krb-postgres
```

After reconnecting to PostgreSQL, the previously registered user still existed.

Therefore:

```text
Spring Boot shutdown
    ↓
Database data remains
```

and:

```text
PostgreSQL container stop
    ↓
PostgreSQL container start
    ↓
Data remains
```

---

# 26. Docker Volume Verification

We inspected the container:

```bash
docker inspect krb-postgres --format='{{json .Mounts}}'
```

Result showed:

```text
Type: volume
Name: krb_postgres_data
Destination: /var/lib/postgresql
Driver: local
RW: true
```

Therefore the PostgreSQL data is backed by a named Docker volume:

```text
krb_postgres_data
```

Important:

- `docker stop/start` was tested successfully.
- We did not delete the container.
- We should not manually modify PostgreSQL files under Docker's volume directory.
- Container deletion vs volume deletion are separate lifecycle events.

---

# 27. Host vs Container Filesystem Issue

We tried navigating to PostgreSQL data directly from Ubuntu's `/var/lib`.

That failed because the PostgreSQL data directory is mounted inside the container.

The Docker volume source is managed under Docker's storage area.

Correct interaction:

```bash
docker exec -it krb-postgres psql -U krb -d krb_enterprise
```

Do not manually modify PostgreSQL's underlying data files.

---

# 28. Test Status

Tests successfully passed after the timezone fix.

Known successful tests:

```text
KEnterpriseApplicationTests
RegisterUserTest
UserTest
SpringSecurityPasswordHasherTest
```

Earlier full run:

```text
Tests run: 8
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

---

# 29. Current Architecture

```text
                         KRB Enterprise
                              │
             ┌────────────────┼────────────────┐
             │                │                │
             ▼                ▼                ▼
          API Layer       Application        Security
             │                │
             │                ▼
             │          Domain Model
             │                │
             │                ▼
             │         UserRepository
             │                ▲
             │                │
             │        Infrastructure
             │                │
             │        PostgresUserRepository
             │                │
             │        UserEntityMapper
             │                │
             │        UserJpaRepository
             │                │
             │             Hibernate
             │                │
             │             Hikari
             │                │
             └──────────── PostgreSQL
                              │
                       Docker named volume
                              │
                    krb_postgres_data
```

Database schema ownership:

```text
Flyway
  ↓
V1
  ↓
V2
  ↓
PostgreSQL

Hibernate
  ↓
validate
  ↓
checks schema
```

---

# 30. Current Database Schema

```text
users
├── id             UUID
├── email          VARCHAR(320)
├── password_hash  VARCHAR(255)
├── status         VARCHAR(20)
├── role           VARCHAR(20)
├── created_at     TIMESTAMP WITH TIME ZONE
└── updated_at     TIMESTAMP WITH TIME ZONE
```

Constraints:

```text
users_pkey
    PRIMARY KEY (id)

uk_users_email
    UNIQUE (email)
```

Flyway:

```text
flyway_schema_history
```

Current migration version:

```text
2
```

---

# 31. Next Step — Testcontainers

The next planned task is integration testing.

Current manual proof:

```text
HTTP
 ↓
UserController
 ↓
RegisterUser
 ↓
UserRepository
 ↓
PostgresUserRepository
 ↓
JPA/Hibernate
 ↓
PostgreSQL
```

We now want an automated integration test proving the same path.

Desired test architecture:

```text
Integration Test
      ↓
Spring Boot
      ↓
PostgresUserRepository
      ↓
JPA/Hibernate
      ↓
Testcontainers PostgreSQL
```

Potential dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

First verify:

```powershell
.\mvnw.cmd dependency:tree | Select-String "testcontainers"
```

Important environment issue to solve first:

- Maven tests run on Windows.
- Docker currently runs inside Ubuntu/VirtualBox.
- We must determine how the Windows test JVM will reach the Docker daemon.
- Do not assume Docker is automatically reachable from Windows.

The VM PostgreSQL remains useful for local development; Testcontainers should provide a disposable database for integration tests.

---

# 32. Learning Lessons

## Lesson 1 — Flyway owns schema evolution

Use:

```text
Flyway migrations
```

instead of manually creating application tables.

## Lesson 2 — Hibernate validates

Use:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

to detect entity/schema mismatch without letting Hibernate change the schema.

## Lesson 3 — Applied migrations should not be edited

If V1 is applied and the schema changes, create V2.

## Lesson 4 — Domain stays independent

Do not put JPA/PostgreSQL/Spring details into the domain model.

## Lesson 5 — Repository abstraction matters

Application depends on:

```text
UserRepository
```

not:

```text
PostgresUserRepository
```

## Lesson 6 — Separate JVMs have separate configuration

Spring Boot and Surefire are separate processes.

## Lesson 7 — Container lifecycle is different from data lifecycle

Container:

```text
krb-postgres
```

Data:

```text
krb_postgres_data
```

The named volume provides persistence.

## Lesson 8 — Manual proof should become automated proof

We first verified registration manually against real PostgreSQL.

Next we automate it with an integration test.

---

# 33. Checkpoint

## Completed

- [x] Spring Boot 4.1.0
- [x] Java 25
- [x] Spring MVC
- [x] Validation
- [x] Spring Security
- [x] User domain
- [x] User registration application service
- [x] Password hashing
- [x] User API
- [x] PostgreSQL
- [x] Docker
- [x] Ubuntu/VirtualBox database environment
- [x] Docker named volume
- [x] JPA/Hibernate
- [x] Flyway
- [x] V1 users migration
- [x] V2 timestamp migration
- [x] UserEntity
- [x] UserEntityMapper
- [x] UserJpaRepository
- [x] PostgresUserRepository
- [x] In-memory repository removed
- [x] Real user persisted
- [x] Persistence verified after container stop/start
- [x] Timezone issue resolved
- [x] Unit tests passing

## Next

- [ ] Verify Testcontainers dependencies
- [ ] Determine Windows → Ubuntu Docker daemon connectivity
- [ ] Add PostgreSQL Testcontainer
- [ ] Create persistence integration test
- [ ] Verify Flyway migrations in integration tests
- [ ] Verify registration against disposable PostgreSQL
- [ ] Continue authentication/user architecture

---

# 34. Command Reference

### Start application

```powershell
.\mvnw.cmd spring-boot:run
```

### Run tests

```powershell
.\mvnw.cmd clean test
```

### Start application with UTC

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Duser.timezone=UTC"
```

### Check Flyway dependencies

```powershell
.\mvnw.cmd dependency:tree | Select-String "flyway"
```

### Check Testcontainers dependencies

```powershell
.\mvnw.cmd dependency:tree | Select-String "testcontainers"
```

### PostgreSQL status

```bash
docker ps
```

### PostgreSQL shell

```bash
docker exec -it krb-postgres psql -U krb -d krb_enterprise
```

### Tables

```sql
\dt
```

### User table

```sql
\d users
```

### Query users

```sql
SELECT
    id,
    email,
    status,
    role,
    created_at,
    updated_at
FROM users;
```

### Stop PostgreSQL

```bash
docker stop krb-postgres
```

### Start PostgreSQL

```bash
docker start krb-postgres
```

### Inspect Docker mount

```bash
docker inspect krb-postgres --format='{{json .Mounts}}'
```

### Inspect volume

```bash
docker volume inspect krb_postgres_data
```

---

# 35. KRB Enterprise Development Method

We are intentionally building incrementally:

```text
Understand
   ↓
Design
   ↓
Build smallest useful slice
   ↓
Run it
   ↓
Observe failure
   ↓
Diagnose root cause
   ↓
Fix
   ↓
Verify
   ↓
Automate verification
   ↓
Document
   ↓
Move to next layer
```

Every important failure should be added to this document with:

```text
Problem
Root Cause
Investigation
Resolution
Lesson
```

This document should be updated continuously as KRB Enterprise evolves.
