# KRB Enterprise — Kubernetes Development Notes
## Session Date: 2026-09-16

## Hands-on Focus

Today's work focused on KRB Enterprise Kubernetes lifecycle behavior:

- Pod lifecycle
- graceful Pod termination
- readiness during termination
- rolling updates
- startup/readiness/liveness probes

## Existing Deployment Context

KRB Enterprise runs in:

```text
Namespace: uat-core
Deployment: krb-enterprise
```

Application traffic flows through:

```text
Ingress
   ↓
KRB Enterprise Service
   ↓
KRB Enterprise Pods
```

The application is deployed with multiple replicas.

## Pod Termination Behavior

The expected lifecycle during termination was reviewed:

```text
Pod termination requested
        ↓
Pod becomes unavailable for new traffic
        ↓
SIGTERM
        ↓
Application graceful shutdown
        ↓
Grace period
        ↓
Container exits
```

The key operational lesson is that terminating a Pod is not equivalent to immediately killing the application.

## KRB Enterprise Health Endpoint

The application's context path is:

```text
/krbenterprise
```

The health endpoint used during UAT verification is:

```text
/krbenterprise/actuator/health
```

The previously verified health response reported:

```json
{
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

## Probe Responsibilities

The KRB Enterprise deployment lifecycle was reviewed using the three Kubernetes probe concepts:

```text
Startup
   → startup protection

Readiness
   → traffic eligibility

Liveness
   → container health
```

These checks have different responsibilities and should not be treated as interchangeable.

## Rolling Deployment Behavior

The intended KRB Enterprise rolling-update flow is:

```text
Existing KRB Enterprise Pods
          ↓
New version starts
          ↓
New Pod passes readiness
          ↓
New Pod becomes eligible for traffic
          ↓
Old Pod is terminated gracefully
```

This helps prevent traffic from being routed to an application instance that has not completed startup.

## Operational Lessons

### 1. Pod termination is also an application concern

Kubernetes initiates termination, but the application must cooperate with graceful shutdown.

### 2. Readiness is critical during deployments

A Pod being alive does not automatically mean it should receive traffic.

### 3. Health probes have different purposes

Startup, readiness, and liveness checks should represent different lifecycle concerns.

### 4. Rolling updates require application cooperation

Deployment strategy, readiness, graceful shutdown, and termination grace periods work together.

## Current KRB Enterprise Kubernetes Architecture

```text
                         Ingress
                            │
                            ▼
                  KRB Enterprise Service
                            │
                 ┌──────────┴──────────┐
                 ▼                     ▼
        KRB Enterprise Pod     KRB Enterprise Pod
                 │                     │
                 └──────────┬──────────┘
                            │
                            ▼
                       PostgreSQL
```

Supporting Kubernetes components already implemented include:

- Deployment
- multiple KRB Enterprise replicas
- ClusterIP Service
- PostgreSQL StatefulSet
- PersistentVolume / PersistentVolumeClaim
- ConfigMap
- Kubernetes Secrets
- RSA key Secret
- ingress-nginx
- Host-based Ingress routing
- Metrics Server
- HPA
- CPU-based autoscaling
- UAT hostname routing

## Next Hands-on Topic

**PodDisruptionBudget (PDB)**

Next, apply PDB concepts to the multi-replica KRB Enterprise Deployment and understand how Kubernetes protects application availability during voluntary disruptions.
