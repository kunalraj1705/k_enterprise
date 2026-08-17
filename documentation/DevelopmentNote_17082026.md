# KRB Enterprise --- Development Documentation

## Yesterday & Today

**Project:** `k_enterprise`\
**Development:** Windows + VS Code\
**Database:** PostgreSQL 18 in Docker on Ubuntu VM\
**Application:** Spring Boot 4.1.0\
**Java:** 25.0.3\
**Spring Security:** 7.1.0\
**Server:** `8282`

------------------------------------------------------------------------

## 1. Environment

``` text
Windows
└── VS Code
    └── Spring Boot application
         │
         ▼
Ubuntu VM
└── Docker
    └── PostgreSQL 18
        └── krb_enterprise
```

PostgreSQL container:

-   Container: `krb-postgres`
-   Image: `postgres:18`
-   Port: `5432`
-   Database: `krb_enterprise`
-   User: `krb`

The agreed deployment target is:

``` text
Ubuntu → Docker → Kubernetes
```

------------------------------------------------------------------------

## 2. Application Configuration

``` properties
spring.application.name=k_enterprise
server.port=8282

spring.datasource.url=jdbc:postgresql://localhost:5432/krb_enterprise
spring.datasource.username=krb
spring.datasource.password=password

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
spring.jpa.open-in-view=false

jwt.expiration-minutes=15
```

The application explicitly uses UTC:

``` java
TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
```

Hibernate uses `ddl-auto=validate`; schema changes are handled by
Flyway.

------------------------------------------------------------------------

## 3. Database and Flyway

### V1 --- users table

``` sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,

    CONSTRAINT uk_users_email UNIQUE (email)
);
```

### V2 --- timestamps

``` sql
ALTER TABLE users
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL;
```

Flyway successfully reported:

``` text
Successfully validated 2 migrations
Current version of schema "public": 2
Schema "public" is up to date
```

------------------------------------------------------------------------

## 4. Existing User

A real user exists in PostgreSQL:

``` text
id:     a0fd87a9-1cba-451a-b499-6491a75aa424
email:  customer@krb.com
status: ACTIVE
role:   CUSTOMER
```

The password is stored as a hash, not plaintext.

------------------------------------------------------------------------

## 5. Domain Model

`User` contains:

``` text
UUID id
String email
String passwordHash
UserRole role
UserStatus status
Instant createdAt
Instant updatedAt
```

Creation defaults to:

``` text
role   → CUSTOMER
status → ACTIVE
```

The domain supports:

``` java
suspend()
activate()
```

and protects against invalid repeated state transitions.

------------------------------------------------------------------------

## 6. Repository Architecture

Domain abstraction:

``` java
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID userId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
```

Infrastructure:

``` text
UserRepository
      ▲
      │
PostgresUserRepository
      │
      ▼
UserJpaRepository
      │
      ▼
PostgreSQL
```

`UserEntityMapper` converts between the domain `User` and persistence
`UserEntity`.

------------------------------------------------------------------------

## 7. UserService Refactor

The old `RegisterUser` application class was removed.

User-related application logic was consolidated into:

``` text
UserService
```

Registration flow:

``` text
UserController
    ↓
UserService.register()
    ↓
existsByEmail()
    ↓
PasswordHasher.hash()
    ↓
User.create()
    ↓
UserRepository.save()
    ↓
PostgreSQL
```

Duplicate email registration is rejected.

------------------------------------------------------------------------

## 8. Password Hashing

The application uses a `PasswordHasher` abstraction.

Infrastructure implementation:

``` text
SpringSecurityPasswordHasher
```

It delegates to Spring Security's `PasswordEncoder`:

``` java
passwordEncoder.encode(rawPassword)
passwordEncoder.matches(rawPassword, passwordHash)
```

Raw passwords are not stored.

------------------------------------------------------------------------

## 9. Registration API

Request:

``` java
public record RegisterUserRequest(
    @NotBlank
    @Email
    String email,

    @NotBlank
    String password) {
}
```

Endpoint:

``` http
POST /api/v1/user
```

Successful registration returns:

``` http
201 Created
```

Response:

``` java
public record UserResponse(
        UUID id,
        String email,
        UserStatus status,
        UserRole role) {
}
```

The password hash is not exposed.

------------------------------------------------------------------------

## 10. Spring Security Authentication

Architecture:

``` text
AuthenticationManager
        ↓
ProviderManager
        ↓
DaoAuthenticationProvider
        ├── UserDetailsService
        └── PasswordEncoder
```

A custom `UserDetailsService` loads users through the application
repository.

The earlier generated Spring Security in-memory password disappeared
after configuring the custom authentication provider.

A startup warning remains because an explicit `AuthenticationProvider`
is intentionally configured; the warning concerns Spring Boot's
automatic `UserDetailsService` configuration and does not indicate
failed authentication.

------------------------------------------------------------------------

## 11. Login API

Login request:

``` java
public record LoginRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        String password) {
}
```

Endpoint:

``` http
POST /api/v1/auth/login
```

`AuthService` uses:

``` java
AuthenticationManager.authenticate(...)
```

Authentication was successfully tested against:

``` text
customer@krb.com
```

The PostgreSQL password hash was successfully verified.

------------------------------------------------------------------------

## 12. JWT Dependency

Added:

``` xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Dependency tree confirmed:

``` text
spring-boot-starter-oauth2-resource-server:4.1.0
spring-security-oauth2-jose:7.1.0
spring-security-oauth2-resource-server:7.1.0
```

We use Spring Security's native JWT support rather than a separate JWT
library.

------------------------------------------------------------------------

## 13. JWT Properties

Created:

``` java
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        long expirationMinutes) {
}
```

Current expiration:

``` properties
jwt.expiration-minutes=15
```

Private/public key material is not stored in `application.properties`.

------------------------------------------------------------------------

## 14. RSA Key Architecture

JWT uses:

``` text
RSA + RS256
```

Concept:

``` text
Private key
    ↓
sign JWT

Public key
    ↓
verify JWT
```

Ubuntu generated a separate RSA key pair using OpenSSL:

``` text
~/workspace/k_enterprise/secrets/
├── private-key.pem
└── public-key.pem
```

The private key permissions were:

``` text
-rw-------
```

For Windows development, a separate RSA key pair was generated outside
the repository:

``` text
C:\Users\Kunal Raj Bhardwaj\.krb-enterprise\secrets\
├── private-key.pem
└── public-key.pem
```

Development and deployment keys are intentionally separate.

------------------------------------------------------------------------

## 15. Git Secret Protection

`.gitignore` was updated:

``` gitignore
### Local secrets ###
secrets/
```

Git verified:

``` text
.gitignore:36:secrets/  secrets
```

The Ubuntu key files were confirmed as ignored.

Private keys must never be committed.

------------------------------------------------------------------------

## 16. RSA Key Configuration

Created:

``` text
RsaKeyConfiguration
```

It reads Windows development PEM files from:

``` text
${user.home}/.krb-enterprise/secrets/
```

The private key is parsed as PKCS#8.

The public key is parsed as X.509.

Spring exposes:

``` text
RSAPrivateKey
RSAPublicKey
```

as beans.

------------------------------------------------------------------------

## 17. JWT Encoder

Created:

``` text
JwtConfiguration
```

It uses:

``` text
NimbusJwtEncoder
```

with an RSA JWK containing the public/private key pair.

Flow:

``` text
RSAPrivateKey + RSAPublicKey
            ↓
       JwtEncoder
            ↓
       signed JWT
```

------------------------------------------------------------------------

## 18. JWT Service

Created:

``` text
JwtService
```

Responsibilities:

``` text
generateToken(User)
```

Claims:

``` text
sub   → User UUID
email → user email
role  → UserRole
iat   → issued-at
exp   → expiration
```

The subject uses the immutable user UUID rather than email.

Token lifetime is 15 minutes.

------------------------------------------------------------------------

## 19. JWT Login Response

`LoginResponse` now contains:

``` java
public record LoginResponse(
        String accessToken,
        String tokenType) {
}
```

Successful login returns:

``` json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer"
}
```

------------------------------------------------------------------------

## 20. Actual JWT Generation Test

A real JWT was generated for:

``` text
customer@krb.com
```

Header:

``` json
{
  "alg": "RS256"
}
```

Payload included:

``` json
{
  "sub": "a0fd87a9-1cba-451a-b499-6491a75aa424",
  "role": "CUSTOMER",
  "email": "customer@krb.com",
  "iat": "...",
  "exp": "..."
}
```

The issued/expiration timestamps confirmed:

``` text
900 seconds
15 minutes
```

This verified RSA signing, claims and expiration.

------------------------------------------------------------------------

## 21. JWT Decoder

Created:

``` text
JwtDecoderConfiguration
```

It exposes:

``` java
@Bean
public JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
    return NimbusJwtDecoder
            .withPublicKey(publicKey)
            .build();
}
```

Incoming JWTs are verified using the RSA public key.

------------------------------------------------------------------------

## 22. Spring Security Resource Server

Spring Security 7.1 requires the JWT customizer form:

``` java
.oauth2ResourceServer(oauth2 ->
        oauth2.jwt(jwt ->
                jwt.decoder(jwtDecoder)))
```

The application uses Spring Security's built-in Bearer-token processing.

A custom JWT filter was deliberately not implemented because Spring
Security already provides:

``` text
BearerTokenAuthenticationFilter
```

Flow:

``` text
Authorization: Bearer <JWT>
        ↓
BearerTokenAuthenticationFilter
        ↓
JwtDecoder
        ↓
RSA public key
        ↓
JWT validation
        ↓
SecurityContext
```

------------------------------------------------------------------------

## 23. Final Security Rules

``` text
POST /api/v1/user
    → public

POST /api/v1/auth/login
    → public

GET /api/v1/user/{id}
    → authenticated

Everything else
    → authenticated
```

The temporary public GET rule was removed.

Testing demonstrated:

``` text
Valid JWT → protected endpoint succeeds
No JWT   → 401 Unauthorized
```

This proves the JWT authentication boundary is working end-to-end.

------------------------------------------------------------------------

## 24. Complete Authentication Architecture

``` text
                     POST /auth/login
                            │
                            ▼
                      AuthController
                            │
                            ▼
                        AuthService
                            │
                            ▼
                   AuthenticationManager
                            │
                            ▼
                  DaoAuthenticationProvider
                     ┌──────┴──────┐
                     ▼             ▼
             UserDetailsService  PasswordEncoder
                     │
                     ▼
                 PostgreSQL
                            │
                            ▼
                      Authentication
                            │
                            ▼
                        JwtService
                            │
                            ▼
                       JwtEncoder
                            │
                      RSA Private Key
                            │
                            ▼
                           JWT


Protected API:
                           JWT
                            │
                            ▼
              BearerTokenAuthenticationFilter
                            │
                            ▼
                       JwtDecoder
                            │
                       RSA Public Key
                            │
                            ▼
                    SecurityContext
                            │
                            ▼
                    Protected endpoint
```

------------------------------------------------------------------------

## 25. Build Verification

The project successfully completed:

``` text
.\mvnw.cmd compile
```

and:

``` text
.\mvnw.cmd clean package -DskipTests
```

Spring Boot started successfully with:

``` text
PostgreSQL 18.4
Flyway migrations validated
Schema version 2
Hibernate initialized
Tomcat started on port 8282
KEnterpriseApplication started
```

The real PostgreSQL-backed login was tested successfully.

------------------------------------------------------------------------

## 26. Testcontainers Decision

Testcontainers work was intentionally not expanded further.

The agreed environment is:

``` text
Windows → VS Code → Spring Boot
Ubuntu → Docker → PostgreSQL
```

The PostgreSQL connection was already working from Windows, so
development focus moved to application functionality instead of
expanding test infrastructure.

------------------------------------------------------------------------

## 27. Current Project State

``` text
User Management
├── User domain
├── UserRole
├── UserStatus
├── UserRepository
└── UserService

Persistence
├── JPA
├── PostgreSQL
├── Flyway
└── Entity mapper

Authentication
├── PasswordEncoder
├── PasswordHasher
├── UserDetailsService
├── DaoAuthenticationProvider
└── AuthenticationManager

JWT
├── RSA keys
├── JwtProperties
├── RsaKeyConfiguration
├── JwtConfiguration
├── JwtEncoder
├── JwtService
├── JwtDecoderConfiguration
└── Bearer token authentication

API
├── POST /api/v1/user
└── POST /api/v1/auth/login
```

------------------------------------------------------------------------

## 28. Not Implemented Yet

### Role-based authorization

The JWT contains:

``` text
role=CUSTOMER
```

but role-based authorization has not yet been fully implemented.

Next:

``` text
CUSTOMER → ROLE_CUSTOMER
ADMIN    → ROLE_ADMIN
```

### User status enforcement

The domain supports:

``` text
ACTIVE
SUSPENDED
```

but security enforcement for suspended users has not yet been
implemented.

### Global exception handling

Exceptions are not yet converted into standardized API errors.

Potential future structure:

``` json
{
  "code": "USER_NOT_FOUND",
  "message": "User not found",
  "timestamp": "...",
  "path": "..."
}
```

### Refresh tokens

Only a 15-minute access token exists currently.

### Password management

Password change/reset functionality has not yet been implemented.

### Production secret management

Development keys are outside the Windows repository.

Deployment should use Docker/Kubernetes secret management rather than
putting private keys in source control or container images.

------------------------------------------------------------------------

## 29. Recommended Next Session

Continue in this order:

``` text
1. Map JWT role → Spring Security authorities
        ↓
2. Implement role-based authorization
        ↓
3. Enforce ACTIVE/SUSPENDED status
        ↓
4. Complete GET user API
        ↓
5. Add suspend/activate APIs
        ↓
6. Add global exception handling
        ↓
7. Standardize validation/error responses
        ↓
8. Decide refresh-token strategy
        ↓
9. Dockerize application
        ↓
10. Kubernetes deployment
        ↓
11. Move secrets to Kubernetes Secrets
        ↓
12. Add observability
```

------------------------------------------------------------------------

## 30. Key Lessons

### Authentication vs Authorization

Authentication answers:

> Who are you?

Current implementation:

``` text
email + password
        ↓
AuthenticationManager
```

Authorization answers:

> What are you allowed to do?

Next phase:

``` text
JWT role
    ↓
Spring Security authority
    ↓
endpoint authorization
```

### Password vs JWT

Password verification happens during login.

Subsequent requests use the JWT:

``` text
Login
  → password verification
  → JWT

Protected request
  → JWT verification
```

### RSA

``` text
Private key → signs
Public key  → verifies
```

### Database vs JWT

PostgreSQL remains the persistent source of user data.

JWT carries the identity/authorization claims needed for request
authentication.

``` text
PostgreSQL
    → persistent user state

JWT
    → request authentication context
```

------------------------------------------------------------------------

# Final Checkpoint

The major milestone reached across these two sessions is:

``` text
User Registration
       ↓
PostgreSQL persistence
       ↓
Password hashing
       ↓
Database-backed authentication
       ↓
Login
       ↓
RSA/RS256 JWT generation
       ↓
Bearer token
       ↓
JWT validation
       ↓
Protected endpoint
       ↓
401 without JWT
```

**Current status: KRB Enterprise has a working PostgreSQL-backed
authentication and JWT authentication flow.**

The next session should begin with **authorization**, not more JWT
infrastructure.
