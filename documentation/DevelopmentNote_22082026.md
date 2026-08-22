# KRB Enterprise — Development Documentation

## Development Progress — 22 August 2026

This document records the work completed and verified today for the `k_enterprise` Spring Boot / Java application.

---

## 1. User Lifecycle

The existing `User` domain already contains the lifecycle states:

```text
ACTIVE
SUSPENDED
```

and the domain operations:

```java
public void suspend() {
    if (this.status == UserStatus.SUSPENDED) {
        throw new IllegalStateException("User is already suspended.");
    }

    status = UserStatus.SUSPENDED;
    updatedAt = Instant.now();
}

public void activate() {
    if (this.status == UserStatus.ACTIVE) {
        throw new IllegalStateException("User is already active.");
    }

    status = UserStatus.ACTIVE;
    updatedAt = Instant.now();
}
```

The lifecycle business rules remain inside the domain model.

---

## 2. Suspend User API

The suspend operation was implemented through:

```http
PATCH /api/v1/user/{userId}/suspend
```

The intended authorization boundary is:

```text
ADMIN       → allowed
OPERATIONS  → allowed
CUSTOMER    → not allowed
```

The application flow is:

```text
Controller
    ↓
UserService
    ↓
User.suspend()
    ↓
UserRepository.save()
    ↓
PostgreSQL
```

---

## 3. Activate User API

The activate operation was implemented through:

```http
PATCH /api/v1/user/{userId}/activate
```

The same authorization boundary applies:

```text
ADMIN       → allowed
OPERATIONS  → allowed
CUSTOMER    → not allowed
```

Flow:

```text
Controller
    ↓
UserService
    ↓
User.activate()
    ↓
UserRepository.save()
    ↓
PostgreSQL
```

---

## 4. Domain State Transition

The lifecycle supports:

```text
ACTIVE
  │
  │ suspend
  ▼
SUSPENDED
  │
  │ activate
  ▼
ACTIVE
```

Invalid repeated transitions are protected by the domain:

### Already suspended

Calling `suspend()` on a suspended user throws:

```text
User is already suspended.
```

### Already active

Calling `activate()` on an active user throws:

```text
User is already active.
```

---

## 5. Authorization

Lifecycle operations are protected using method-level authorization.

The intended rule is:

```java
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
```

The authorization flow is:

```text
JWT
 ↓
role claim
 ↓
JwtAuthenticationConverter
 ↓
ROLE_ADMIN / ROLE_OPERATIONS / ROLE_CUSTOMER
 ↓
@PreAuthorize
 ↓
Allow or 403
```

---

## 6. Lifecycle Verification

Both lifecycle operations were tested successfully.

### Suspend

```http
PATCH /api/v1/user/{userId}/suspend
```

The user transitioned:

```text
ACTIVE → SUSPENDED
```

### Activate

```http
PATCH /api/v1/user/{userId}/activate
```

The user transitioned:

```text
SUSPENDED → ACTIVE
```

Both operations were verified through the application.

---

## 7. Database Persistence Verification

After verifying the API behavior, the persisted database state was also checked.

Example query:

```sql
SELECT user_id, email, status, role, updated_at
FROM users
ORDER BY updated_at DESC;
```

The database correctly reflected the lifecycle transitions.

### Suspend persistence

```text
API
 ↓
ACTIVE → SUSPENDED
 ↓
PostgreSQL
 ↓
status = SUSPENDED
```

### Activate persistence

```text
API
 ↓
SUSPENDED → ACTIVE
 ↓
PostgreSQL
 ↓
status = ACTIVE
```

The `updated_at` value was also updated by the domain operation.

This verified the complete persistence path rather than only verifying the HTTP response.

---

## 8. Complete User Management Vertical Slice

The user-management functionality is now substantially complete and verified.

```text
User Management
│
├── Registration
│   ├── Customer registration
│   └── Admin user creation
│
├── Authentication
│   └── JWT
│
├── Authorization
│   ├── CUSTOMER
│   ├── ADMIN
│   └── OPERATIONS
│
├── User Lookup
│   ├── Current authenticated user
│   ├── Business userId
│   └── UUID
│
├── Ownership
│   └── CUSTOMER → own user only
│
├── User Lifecycle
│   ├── Suspend
│   └── Activate
│
└── Error Handling
    ├── Validation
    ├── 401
    ├── 403
    ├── 404
    └── Application errors
```

---

## 9. Security Model Verified

### Authentication

```text
JWT
 ↓
JwtDecoder
 ↓
SecurityContext
```

### Role conversion

```text
CUSTOMER    → ROLE_CUSTOMER
ADMIN       → ROLE_ADMIN
OPERATIONS  → ROLE_OPERATIONS
```

### Authorization

```text
@PreAuthorize
 ↓
Role / ownership decision
 ↓
Controller access or 403
```

### Error handling

```text
No/invalid authentication → 401
Insufficient permission    → 403
Resource not found         → 404
Application error          → appropriate application response
```

---

## 10. Application Architecture Verified

The lifecycle implementation follows the established layered architecture:

```text
HTTP Request
      ↓
Controller
      ↓
Authorization
      ↓
Application Service
      ↓
Domain
      ↓
Repository
      ↓
PostgreSQL
```

The domain owns the state transition rules:

```text
User.suspend()
User.activate()
```

The application service coordinates the use case.

The repository handles persistence.

The controller handles HTTP concerns.

---

## 11. Milestone Status

The first KRB Enterprise vertical slice is now ready to move toward infrastructure learning.

### Completed

- [x] User registration
- [x] Customer registration
- [x] ADMIN-only user creation
- [x] UUID identity
- [x] Business `userId`
- [x] Role-based user ID generation
- [x] PostgreSQL persistence
- [x] Flyway migrations
- [x] JWT authentication
- [x] JWT `sub = userId`
- [x] SecurityContext current-user lookup
- [x] JWT role conversion
- [x] `@EnableMethodSecurity`
- [x] Role-based authorization
- [x] Customer ownership authorization
- [x] Custom 401 handling
- [x] Custom 403 handling
- [x] Generic application exception handling
- [x] User lookup by UUID
- [x] User lookup by business `userId`
- [x] User lifecycle — suspend
- [x] User lifecycle — activate
- [x] Lifecycle API verification
- [x] Lifecycle database verification

---

## 12. Decision: Move to Docker

We decided not to continue adding business features indefinitely.

The current user-management vertical slice is sufficient to become the foundation for the infrastructure phase.

The next roadmap phase is:

```text
KRB Enterprise
      ↓
Docker
      ↓
Docker Compose
      ↓
Kubernetes
```

The application will be used as the real project for learning deployment rather than creating a separate toy application.

---

## 13. Docker Phase — Planned Flow

The next phase will containerize the existing Spring Boot application.

```text
Spring Boot Application
        ↓
Dockerfile
        ↓
Build Application Image
        ↓
Run Spring Boot Container
        ↓
PostgreSQL Container
        ↓
Docker Network
        ↓
Environment Configuration
        ↓
Docker Compose
```

The Docker phase will cover:

- Spring Boot containerization
- Multi-stage Docker build
- Runtime image
- Environment variables
- PostgreSQL container
- Container networking
- Flyway migrations
- Application configuration
- Persistent database storage
- Docker Compose

---

## 14. Kubernetes Phase — Planned

After Docker and Docker Compose are working, the application will move to Kubernetes.

Planned concepts:

```text
Docker Image
     ↓
Kubernetes
     ├── Pod
     ├── Deployment
     ├── Service
     ├── ConfigMap
     ├── Secret
     ├── Health Probes
     ├── Persistent Storage
     └── Scaling
```

The goal is to deploy the actual KRB Enterprise application and understand its behavior in a container orchestration environment.

---

## 15. Learning Strategy

The roadmap will follow this cycle:

```text
Build
  ↓
Containerize
  ↓
Orchestrate
  ↓
Deploy
  ↓
Observe
  ↓
Improve
  ↓
Build next feature
```

This keeps infrastructure learning connected to a real backend application.

---

## 16. End-of-Day Architecture

```text
                         ┌─────────────────┐
                         │     Client      │
                         └────────┬────────┘
                                  │
                                  ▼
                    ┌──────────────────────────┐
                    │    Spring Security       │
                    │                          │
                    │ JWT Decoder              │
                    │ JWT Converter            │
                    │ Authentication           │
                    │ Authorization            │
                    └────────────┬─────────────┘
                                 │
                                 ▼
                         UserController
                                 │
                                 ▼
                           UserService
                                 │
                                 ▼
                           User Domain
                                 │
                                 ▼
                         UserRepository
                                 │
                                 ▼
                            PostgreSQL
```

Lifecycle flow:

```text
PATCH /suspend
       ↓
Authorization
       ↓
UserService
       ↓
User.suspend()
       ↓
Repository
       ↓
PostgreSQL
       ↓
SUSPENDED
```

and:

```text
PATCH /activate
       ↓
Authorization
       ↓
UserService
       ↓
User.activate()
       ↓
Repository
       ↓
PostgreSQL
       ↓
ACTIVE
```

---

## 17. Today's Verification Summary

Today we verified:

- Suspend API works.
- Activate API works.
- Lifecycle state changes correctly.
- Authorization is applied to lifecycle operations.
- Database status changes correctly.
- `updated_at` changes with lifecycle operations.
- The domain owns lifecycle transition rules.
- The complete Controller → Service → Domain → Repository → PostgreSQL flow works.

---

## 18. Next Development Session

The next task is to begin **Dockerizing KRB Enterprise**.

We should start with the existing application and understand:

1. What the application needs at runtime.
2. How the Spring Boot JAR is packaged.
3. How the Docker image is built.
4. How PostgreSQL is provided.
5. How application configuration is injected.
6. How Flyway runs in the containerized setup.
7. How the application and PostgreSQL containers communicate.
8. How persistent PostgreSQL data is maintained.

Then:

```text
Docker
  ↓
Docker Compose
  ↓
Kubernetes
```

---

**End of development checkpoint — 22 August 2026**
