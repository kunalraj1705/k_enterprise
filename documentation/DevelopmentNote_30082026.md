# KRB Enterprise — Environment Configuration Changes

## Session Summary
Today we introduced environment-based Spring Boot configuration for KRB Enterprise.

## 1. Environments
The application now uses:

```text
DEV
UAT
PROD
```

Profile names:

```text
dev
uat
prod
```

Structure:

```text
src/main/resources/
├── application.properties
├── application-dev.properties
├── application-uat.properties
└── application-prod.properties
```

## 2. Common Configuration
`application.properties` contains shared configuration:

```properties
spring.application.name=k_enterprise
server.port=8282

spring.profiles.default=dev

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
spring.jpa.open-in-view=false

jwt.expiration-minutes=15
```

Important addition:

```properties
spring.profiles.default=dev
```

When no profile is explicitly activated:

```text
No active profile → dev → application-dev.properties
```

## 3. DEV Configuration
Development is performed on Windows.

`application-dev.properties` keeps local database values:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/krb_enterprise
spring.datasource.username={{username}}
spring.datasource.password={{password}}
```

No Windows environment variables are required for this workflow.

## 4. UAT and PROD Deployment Design
For deployment, datasource values will be supplied at startup from secrets:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

Target flow:

```text
Deployment secrets
    ↓
DB_URL / DB_USERNAME / DB_PASSWORD
    ↓
Spring Boot startup
    ↓
UAT or PROD configuration
    ↓
PostgreSQL connection
```

Sensitive deployment values should not be baked into the application image.

## 5. Environment Model

```text
Windows
└── DEV development

Ubuntu
├── UAT deployment
└── PROD deployment
```

The same application image can later run with different runtime configuration.

## 6. PostgreSQL Recovery
The persistent volume was verified:

```text
krb_postgres_data
```

The existing container `krb-postgres` was already present, so it was started with:

```bash
docker start krb-postgres
```

The application successfully started after PostgreSQL was running.

## Current Status

```text
Spring Boot application
    ↓
Profiles introduced
    ↓
Default profile = dev
    ↓
PostgreSQL running
    ↓
Application up successfully
```

## Next Session
Begin Docker Compose and prepare the deployment architecture for:

- KRB Enterprise Spring Boot application
- PostgreSQL
- Docker network
- Persistent volume
- Environment-specific configuration
- UAT/PROD secret handling during deployment
