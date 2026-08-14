# KRB Enterprise — Development Notes
## 14 August 2026

## Session goal
Continue the KRB Enterprise environment setup and establish reliable integration testing inside Ubuntu, with Maven/Testcontainers running next to the Ubuntu Docker Engine.

Architecture:
Windows PowerShell → SSH → VirtualBox Ubuntu → Docker → Testcontainers → PostgreSQL → Spring Boot

## 1. Restored Windows → Ubuntu SSH connectivity

SSH and PostgreSQL connectivity had stopped working from Windows. Investigation showed `enp0s3` was UP but had lost its IPv4 DHCP lease.

Important addresses:
- `10.0.2.15` — Ubuntu VM / VirtualBox NAT IPv4
- `172.17.0.1` — Docker bridge
- `127.0.0.1` — loopback

NetworkManager showed:
- connection: `netplan-enp0s3`
- IPv4 method: DHCP/auto
- no IPv4 address/gateway/routes

Fix:
```bash
sudo nmcli connection down netplan-enp0s3
sudo nmcli connection up netplan-enp0s3
```

Ubuntu regained:
```text
10.0.2.15/24
```

Working Windows SSH command:
```powershell
ssh -p 2222 ubuntu@127.0.0.1
```

VirtualBox path:
```text
Windows 127.0.0.1:2222
        ↓
VirtualBox NAT
        ↓
Ubuntu 10.0.2.15:22
```

## 2. IPv4 vs IPv6

Ubuntu's `enp0s3` has IPv4 and IPv6, and `sshd` listens on both:
```text
0.0.0.0:22
[::]:22
```

The current VirtualBox forwarding rule is IPv4-based, so SSH uses:
```text
127.0.0.1:2222 → 10.0.2.15:22
```

`172.17.0.1` is Docker's bridge address and is not the VM's SSH address.

Decision: keep the working IPv4 + VirtualBox NAT setup and avoid unnecessary IPv6 changes.

## 3. PostgreSQL/Docker connectivity

Existing container:
```text
krb-postgres
postgres:18
0.0.0.0:5432->5432/tcp
```

PostgreSQL was healthy:
```bash
docker exec krb-postgres pg_isready -U krb -d krb_enterprise
```

Result:
```text
/var/run/postgresql:5432 - accepting connections
```

Conclusion: Docker/PostgreSQL were not the problem; the lost Ubuntu IPv4 lease caused the Windows connectivity issue.

## 4. Testcontainers strategy

Previous Windows-based Testcontainers had failed because Ryuk used dynamically mapped ports that were not reachable correctly across Windows → VirtualBox Ubuntu → Docker.

Typical previous failure:
```text
Could not connect to Ryuk at 127.0.0.1:32769
Timed out waiting for Ryuk container to start
```

Decision:
Run Maven/Testcontainers directly inside Ubuntu, next to the Docker Engine.

Target:
```text
Ubuntu
  ↓
Maven
  ↓
Testcontainers
  ↓
local Docker Engine
```

## 5. First Testcontainers test

There was initially no Testcontainers test. We created:
```text
src/test/java/com/krb/enterprise/user/infrastructure/PostgresIntegrationTest.java
```

Initial test:
```java
@Testcontainers
class PostgresIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18");

    @Test
    void shouldStartPostgresContainer() {
        assertTrue(postgres.isRunning());
    }
}
```

The first compilation failed:
```text
package org.testcontainers.junit.jupiter does not exist
```

Cause: missing JUnit 5 integration module.

Added to `pom.xml`:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

No explicit version was added because Spring Boot dependency management manages the Testcontainers version.

## 6. Testcontainers startup checkpoint

Command:
```bash
./mvnw -Dtest=PostgresIntegrationTest test
```

Result:
```text
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

This proved:
```text
Ubuntu
 ↓
Maven
 ↓
Testcontainers 2.0.5
 ↓
Docker 29.1.3
 ↓
postgres:18
 ↓
JUnit
```

Afterward, `docker ps` showed only the manually managed `krb-postgres`; the temporary Testcontainers container had been cleaned up.

## 7. Real JDBC connectivity

We then extended `PostgresIntegrationTest` to:
1. verify the container is running;
2. obtain the Testcontainers-generated JDBC URL;
3. obtain username/password;
4. open a JDBC connection;
5. execute `SELECT 1`;
6. assert the result is `1`.

The test succeeded.

This proved:
```text
Testcontainers → PostgreSQL → JDBC → SELECT 1 → JUnit
```

The test does not depend on the manually managed `localhost:5432` database.

## 8. Current application database configuration

`src/main/resources/application.properties` contains:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/krb_enterprise
spring.datasource.username=krb
spring.datasource.password=lol

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
spring.jpa.open-in-view=false
```

Flyway migrations:
```text
src/main/resources/db/migration/V1__create_users_table.sql
src/main/resources/db/migration/V2__add_user_timestamps.sql
```

Decision: do not change normal `application.properties` for Testcontainers. Integration tests should dynamically override datasource properties.

## 9. Spring Boot + Testcontainers

Created:
```text
src/test/java/com/krb/enterprise/user/infrastructure/PostgresSpringBootIntegrationTest.java
```

Core setup:
```java
@Testcontainers
@SpringBootTest
class PostgresSpringBootIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldStartSpringBootWithPostgres() {
        assertTrue(postgres.isRunning());
    }
}
```

Command:
```bash
./mvnw -Dtest=PostgresSpringBootIntegrationTest test
```

Result:
```text
SUCCESS
```

This proved:
```text
Testcontainers PostgreSQL
        ↓
Dynamic datasource properties
        ↓
Spring Boot application context
        ↓
SUCCESS
```

## 10. Git divergence and synchronization

Ubuntu and GitHub temporarily diverged.

Remote:
```text
fef5b98 Devnote
```

Local:
```text
9fba0b8 test: add Testcontainers PostgreSQL integration
```

Working tree was clean. The local Testcontainers commit was rebased on top of the remote Devnote commit and pushed successfully.

Both changes were preserved.

## 11. Current project state

Environment:
```text
Ubuntu 26.04 LTS
Java 25.0.3
Maven 3.9.x / Maven Wrapper
Git 2.53.0
Docker Engine 29.1.3
PostgreSQL 18
```

Relevant dependencies:
```text
Spring Boot 4.1.0
Testcontainers 2.0.5
testcontainers-postgresql 2.0.5
testcontainers-junit-jupiter 2.0.5
JUnit Jupiter 6.0.3
```

Proven working:
```text
Windows → SSH → Ubuntu
Ubuntu → Docker
Ubuntu → Testcontainers
Testcontainers → PostgreSQL
Testcontainers → JDBC
Testcontainers → Spring Boot
```

## 12. Exact stopping point

We stopped after successfully proving Spring Boot can start against the Testcontainers PostgreSQL instance.

Next logical layer:
```text
Testcontainers PostgreSQL
        ↓
Spring Boot
        ↓
Flyway V1 + V2
        ↓
Hibernate/JPA validation
        ↓
Repository
        ↓
INSERT user
        ↓
SELECT user
        ↓
assert
```

Do not modify repository/business tests until this next integration layer is implemented.

## Key lessons

1. VirtualBox NAT can lose its DHCP IPv4 lease while the interface remains connected.
2. `172.17.0.1` is Docker's bridge address, not Ubuntu's VM address.
3. `10.0.2.15` is the current Ubuntu VM's VirtualBox NAT IPv4 address.
4. SSH uses `127.0.0.1:2222 → 10.0.2.15:22`.
5. PostgreSQL became reachable again after Ubuntu regained IPv4.
6. Running Testcontainers inside Ubuntu avoids the previous Windows-to-remote-Docker Ryuk networking issue.
7. `testcontainers-junit-jupiter` is required for `@Testcontainers` and `@Container`.
8. Testcontainers provides dynamic PostgreSQL connection details; tests should not depend on `localhost:5432`.
9. `@DynamicPropertySource` connects Testcontainers' dynamic datasource information to Spring Boot.
10. Integration testing should progress incrementally: container lifecycle → JDBC → Spring Boot → Flyway/JPA → repository → business flow.
