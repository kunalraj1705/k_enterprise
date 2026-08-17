# KRB Enterprise --- Development Notes

## 13 August 2026

## Goal

Set up a clean Ubuntu development/integration environment for
`k_enterprise`, keeping Windows for coding/Git and Ubuntu for Linux
builds, Docker, and Testcontainers.

Architecture:

``` text
Windows → Git push → GitHub → Ubuntu git pull
Ubuntu → Maven/Testcontainers → local Ubuntu Docker → PostgreSQL
```

## 1. Previous Docker/Testcontainers problem

Docker was running inside the Ubuntu VirtualBox VM. Windows could reach
the Docker API through `127.0.0.1:23750`, but Testcontainers from
Windows failed because Ryuk used dynamically mapped ports that were not
reachable correctly across the Windows → Ubuntu VM boundary.

Typical failure:

``` text
Could not connect to Ryuk at 127.0.0.1:32769
Timed out waiting for Ryuk container to start
```

Decision: stop using Windows as the Testcontainers client. Run
Maven/Testcontainers inside Ubuntu, next to Docker.

## 2. SSH to Ubuntu

The Ubuntu VM was reachable at a private address, but direct:

``` powershell
ssh your-user@your-vm-host
```

timed out.

VirtualBox port forwarding was used instead:

``` powershell
ssh -p 2222 your-user@127.0.0.1
```

This successfully opened Ubuntu 26.04 LTS.

## 3. Ubuntu environment

Verified: - Ubuntu 26.04 LTS - Java 25.0.3 - Git 2.53.0 - Maven 3.9.x -
Docker Engine 29.1.3 - PostgreSQL 18 container `krb-postgres`

## 4. Storage cleanup

Root disk:

``` text
49G total
12G used
36G available
25% used
```

Largest areas included:

``` text
/usr                 ~5.8G
/var                 ~5.0G
/var/lib/snapd       ~3.4G
/var/lib/docker      ~866M
```

Snap had many disabled/old revisions. Old disabled revisions were
removed with:

``` bash
snap list --all | awk '/disabled/{print $1, $3}' | while read snap rev; do sudo snap remove "$snap" --revision="$rev"; done
```

APT cache was cleaned:

``` bash
sudo apt clean
```

Journal usage was checked:

``` bash
journalctl --disk-usage
```

and was about 73.5M.

Snap cache was investigated and found to be very large (\~2.4G). It was
stopped/cleaned, but a significant amount remained. We deliberately did
not blindly delete active Snap state.

Storage was healthy enough, so we returned to the main project.

## 5. Git workspace

Ubuntu workspace:

``` text
~/workspace/your-project
```

Repository:

``` text
git@github.com:your-org/your-project.git
```

The repository was cloned cleanly into Ubuntu rather than copying the
Windows project.

## 6. Maven wrapper permission

After cloning:

``` bash
./mvnw -version
```

returned `Permission denied`.

Cause: Linux executable permission was missing.

Fix:

``` bash
chmod +x mvnw
```

The mode changed from `100644` to `100755`. The permission change was
committed and pushed.

Do not use `sudo ./mvnw`; Maven should run as the normal `ubuntu` user.

## 7. GitHub SSH authentication

HTTPS push failed because GitHub no longer accepts account passwords for
Git operations.

Ubuntu initially had no GitHub SSH key:

``` text
Permission denied (publickey)
```

An Ed25519 key was created and added to GitHub.

Verification:

``` bash
ssh -T git@github.com
```

Successful result:

``` text
Hi your-github-user! You've successfully authenticated, but GitHub does not provide shell access.
```

The Git remote was changed from HTTPS to SSH:

``` text
git@github.com:your-org/your-project.git
```

Ubuntu can now `git pull` and `git push` without GitHub passwords.

## 8. Testcontainers dependencies

`pom.xml` contains:

``` xml
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
```

Dependency tree confirmed:

``` text
spring-boot-testcontainers:4.1.0
└── testcontainers:2.0.5

testcontainers-postgresql:2.0.5
└── testcontainers-jdbc:2.0.5
    └── testcontainers-database-commons:2.0.5
```

JUnit Jupiter 6.0.3 is also present.

## 9. Previous experimental test

`TestcontainersConnectionTest.java` was a temporary
Windows/remote-Docker experiment. It failed because of Ryuk networking
and was removed.

We intentionally did not keep failed experimental code in the
repository.

## 10. Java compiler issue

Initial Ubuntu build failed:

``` text
error: release version 25 not supported
```

Investigation showed:

``` text
java -version → Java 25.0.3
mvn -version  → Java 25.0.3
javac         → NOT FOUND
```

The runtime was installed, but the JDK/compiler was missing.

Fix:

``` bash
sudo apt update
sudo apt install openjdk-25-jdk
```

After installing the JDK, Java 25 compilation worked.

## 11. Full project test result

Running:

``` bash
./mvnw clean test
```

succeeded.

Result:

``` text
Tests run: 12
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

This established a clean Linux build.

## 12. Docker verification

``` bash
docker ps
```

showed the existing local PostgreSQL:

``` text
postgres:18
krb-postgres
Up
0.0.0.0:5432->5432
```

Docker version:

``` bash
docker info --format '{{.ServerVersion}}'
```

returned:

``` text
29.1.3
```

This confirms Ubuntu can directly access its local Docker Engine.

## 13. Current test suite

Existing tests:

``` text
src/test/java/com/krb/enterprise/KEnterpriseApplicationTests.java
src/test/java/com/krb/enterprise/user/domain/UserTest.java
src/test/java/com/krb/enterprise/user/application/RegisterUserUseCaseTest.java
src/test/java/com/krb/enterprise/user/infrastructure/security/SpringSecurityPasswordHasherTest.java
```

No Testcontainers test existed yet.

## 14. Next Testcontainers milestone

The next test is intentionally small: prove that Testcontainers running
inside Ubuntu can start a PostgreSQL 18 container.

Planned file:

``` text
src/test/java/com/krb/enterprise/user/infrastructure/PostgresIntegrationTest.java
```

Planned test:

``` java
package com.krb.enterprise.user.infrastructure;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

Run only this test first:

``` bash
./mvnw -Dtest=PostgresIntegrationTest test
```

Do not run the entire suite until this isolated test is understood.

## 15. Future integration test

After the basic container-start test passes, evolve it into a real KRB
Enterprise integration test:

``` text
Testcontainers PostgreSQL
        ↓
Spring Boot
        ↓
Flyway
        ↓
users table
        ↓
PostgresUserRepository
        ↓
RegisterUser
        ↓
INSERT
        ↓
PostgreSQL
        ↓
assert persisted user
```

The existing manually managed `krb-postgres` container should remain
separate. Testcontainers should create an isolated database container
for integration tests and clean it up automatically.

## 16. End-of-day checkpoint

Ready: - Ubuntu 26.04 LTS - Java 25.0.3 - JDK/javac 25.0.3 - Maven
Wrapper - Git 2.53.0 - GitHub SSH - Docker 29.1.3 - PostgreSQL 18 -
Clean Git repository - 12 existing tests passing - Testcontainers
dependencies present

Next command tomorrow:

``` bash
cd ~/workspace/your-project
./mvnw -Dtest=PostgresIntegrationTest test
```

## Key lessons

1.  Remote Docker from Windows adds networking complexity for
    Testcontainers/Ryuk.
2.  Running Testcontainers on the same Ubuntu VM as Docker is the
    cleaner architecture.
3.  Linux requires executable permission for `mvnw`.
4.  GitHub Git operations should use SSH or a supported token, not an
    account password.
5.  `java` being installed does not mean `javac` is installed; Maven
    compilation requires the JDK.
6.  Start integration testing small: container startup → application
    connection → real repository/business flow.
