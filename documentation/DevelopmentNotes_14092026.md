# KRB Enterprise --- Kubernetes Development Notes

## Date: 2026-09-14

### 1. Kubernetes Ingress Added

KRB Enterprise now has an application-specific Ingress resource.

``` text
k_enterprise_infra/
└── kubernetes/
    └── base/
        └── krbenterprise/
            ├── deployment.yaml
            ├── service.yaml
            └── ingress.yaml
```

### 2. KRB Enterprise Ingress

Current `ingress.yaml`:

``` yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: krbenterprise
  namespace: uat-core
spec:
  ingressClassName: nginx
  rules:
    - host: uat-customer.krbenterprise.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: krb-enterprise
                port:
                  number: 8282
```

The current UAT hostname is:

``` text
uat-customer.krbenterprise.com
```

Requests are routed to the existing `krb-enterprise` Service on port
`8282`.

### 3. Ingress Controller

No Ingress Controller was present initially.

An **ingress-nginx** controller was installed. Its Service is exposed
as:

``` text
ingress-nginx-controller   NodePort
HTTP   80:31851
HTTPS  443:30419
```

HTTP traffic therefore reaches the controller through:

``` text
10.0.2.15:31851
```

### 4. Ingress Verification

The Ingress was created successfully:

``` text
NAME            CLASS   HOSTS                            PORTS
krbenterprise   nginx   uat-customer.krbenterprise.com   80
```

Routing was verified from the Ubuntu node:

``` bash
curl -i -H "Host: uat-customer.krbenterprise.com"   http://10.0.2.15:31851/krbenterprise/actuator/health
```

Result:

``` text
HTTP/1.1 200
```

Application response:

``` json
{"groups":["liveness","readiness"],"status":"UP"}
```

This confirmed:

``` text
HTTP request
    ↓
NodePort 31851
    ↓
ingress-nginx
    ↓
Host-based Ingress rule
    ↓
krb-enterprise Service :8282
    ↓
KRB Enterprise Pods
    ↓
Spring Boot application
```

### 5. Windows / Postman Access

For the current VirtualBox NAT-based UAT setup, Windows hostname
resolution was configured with:

``` text
127.0.0.1 uat-customer.krbenterprise.com
```

VirtualBox NAT forwards the Windows host HTTP port to the Ubuntu VM's
ingress-nginx NodePort.

The final Postman URL is:

``` text
http://uat-customer.krbenterprise.com/krbenterprise/actuator/health
```

This was successfully tested.

### 6. Current KRB Enterprise Kubernetes Architecture

``` text
Windows / Postman
        ↓
uat-customer.krbenterprise.com
        ↓
VirtualBox NAT
        ↓
Ubuntu Node
        ↓
ingress-nginx NodePort :31851
        ↓
Ingress: krbenterprise
        ↓
Service: krb-enterprise :8282
        ↓
┌─────────────────────┐
│ KRB Enterprise Pod  │
│ KRB Enterprise Pod  │
└─────────────────────┘
```

PostgreSQL remains behind its Kubernetes Service and StatefulSet/PV/PVC
setup.

### 7. Completed Today

-   Installed ingress-nginx.
-   Created the KRB Enterprise application-specific `ingress.yaml`.
-   Configured hostname-based routing.
-   Connected Ingress to `krb-enterprise` Service.
-   Verified routing with `curl`.
-   Verified Windows/Postman access.
-   Established the hostname pattern for future applications.

### 8. Deferred

-   HTTPS/TLS.
-   Production DNS.
-   Production LoadBalancer/external load-balancer integration.
-   Additional application hostnames.
