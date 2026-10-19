# KRB Academy --- KRB Enterprise AWS EKS Hands-on Development Notes

## Session Date: 2026-10-19

## Objective

Continue KRB Enterprise deployment troubleshooting on AWS EKS, resolve
application startup failures, verify application health, review EKS
configuration changes, and prepare a shutdown plan to avoid unnecessary
AWS charges.

## 1. EKS Cluster Review and Configuration

Worked with the EKS cluster:

``` text
Cluster: krb-enterprise-eks
Region: ap-southeast-2 (Sydney)
Kubernetes version: 1.37
Authentication mode: EKS API
API endpoint: Public and private access enabled
```

Reviewed the original EKS Auto Mode setup, including the
`general-purpose` and `system` node pools, cluster/node IAM roles,
add-ons, and EKS access policies.

Relevant IAM roles:

``` text
Cluster role: krb-enterprise-eks-cluster-role
Node role: krb-enterprise-eks-node-role
EC2 access role: krb-academy-ec2-role
```

The cluster was upgraded from Kubernetes 1.36 to 1.37 on October 12.

Today's infrastructure iterations included: - Installing/reapplying
`vpc-cni` version `v1.22.4-eksbuild.3`. - Installing `kube-proxy`
version `v1.37.0-eksbuild.22`. - Installing `coredns` version
`v1.14.7-eksbuild.11`. - Reviewing access entries and policies for the
node IAM role. - Toggling EKS Auto Mode during troubleshooting. -
Creating and deleting managed node groups while testing instance types
and AMIs.

Final managed node group configuration recorded:

``` text
Instance type: t3.small
AMI: Amazon Linux 2023, x86_64
Desired capacity: 1
Scaling range: 1–2 nodes
Subnets: 2
```

The metrics-server add-on was installed with a node selector targeting
the `system` node pool. Metrics-server Pods were observed Pending during
troubleshooting; this remains an item to verify.

## 2. Kubernetes Workload and Database Configuration

Worked in namespace `krb-enterprise`.

Workloads and Service names:

``` text
Application Deployment: krb-enterprise
PostgreSQL Deployment: postgres
PostgreSQL Service: postgres
PostgreSQL port: 5432
```

The application initially entered `CrashLoopBackOff`. The datasource URL
was configured to use `localhost:5432`, which points to the application
container itself rather than the PostgreSQL Service.

Updated datasource URL:

``` text
jdbc:postgresql://postgres:5432/krb_enterprise
```

Database credentials were configured using the existing Kubernetes
Secret `postgres-secret`, with the `POSTGRES_USER` and
`POSTGRES_PASSWORD` keys.

## 3. PostgreSQL Storage Troubleshooting

The PostgreSQL Pod was initially Pending because persistent-volume
provisioning was not working as expected.

A custom StorageClass named `auto-ebs-sc` was created using the
provisioner:

``` text
ebs.csi.eks.amazonaws.com
```

Provisioning encountered a topology error:

``` text
no topology key found for node
```

The persistent-storage setup was removed from the immediate
troubleshooting path. PostgreSQL was recreated using the official
`postgres:18` image with `emptyDir` storage.

Important limitation:

``` text
emptyDir storage is temporary.
Database data is lost when the Pod is removed or recreated.
```

Durable PostgreSQL storage remains unresolved and should be configured
before treating this environment as persistent.

## 4. RSA Key Secret and Application Startup

The application also failed because the expected RSA public-key file was
missing:

``` text
/root/.krb-enterprise/secrets/public-key.pem
```

Actions completed: - Generated a new RSA key pair on the EC2 instance. -
Created the Kubernetes Secret `krb-enterprise-rsa`. - Patched the
`krb-enterprise` Deployment to mount the Secret read-only at
`/root/.krb-enterprise/secrets`.

The application subsequently started successfully.

Important note: generating a new RSA key pair can invalidate JWTs signed
with a previous private key or make previously issued tokens fail
validation, depending on the application's key-validation configuration.

## 5. Application Health Verification

Confirmed that the application started and embedded Tomcat was listening
on port `8282`.

Startup details:

``` text
Tomcat port: 8282
Context path: /krbenterprise
Application: KEnterpriseApplication
```

Used Kubernetes port forwarding and called the Actuator health endpoint:

``` bash
kubectl port-forward deployment/krb-enterprise -n krb-enterprise 8282:8282
curl -i http://localhost:8282/krbenterprise/actuator/health
```

Observed response:

``` text
HTTP 200 OK
```

``` json
{
  "groups": ["liveness", "readiness"],
  "status": "UP"
}
```

The port-forward process was stopped with `Ctrl+C` after verification.
Stopping port forwarding does not stop the Kubernetes workload.

## 6. AWS CLI Access and IAM

Attempted to list EKS clusters from the EC2 instance:

``` bash
aws eks list-clusters --region ap-southeast-2
```

The command failed with `AccessDenied` because the assumed role
`krb-academy-ec2-role` was not authorized for `eks:ListClusters`.

The role had previously been granted `AmazonEKSClusterAdminPolicy`
through an EKS access entry for Kubernetes cluster access. That
Kubernetes access policy does not automatically grant all AWS IAM
permissions needed to call AWS management APIs.

An authorized AWS Console identity or appropriately scoped IAM
permissions are required to complete cluster-level AWS cleanup through
the CLI.

## 7. AWS Shutdown and Cost Review

The goal was to stop AWS resources when development work is finished.

Shutdown actions discussed: - Scale the `krb-enterprise` and `postgres`
Deployments to zero if the workloads should be stopped. - Scale down or
delete the managed node group to stop worker-node compute charges. -
Decide whether to delete `krb-enterprise-eks` if the cluster is no
longer needed. - Check whether the earlier cluster attempt named
`thoughtful-blues-goose` still exists. - Review EBS volumes, load
balancers, public IPv4 addresses, NAT Gateways, and CloudWatch log
groups for residual resources and charges.

The following commands were proposed to stop the two Deployments:

``` bash
kubectl scale deployment krb-enterprise --replicas=0 -n krb-enterprise
kubectl scale deployment postgres --replicas=0 -n krb-enterprise
kubectl get pods -n krb-enterprise
```

Execution of these scale commands was not confirmed in the available
session notes.

Important: - Scaling Deployments to zero does not stop EKS control-plane
charges or remove all AWS resources. - Deleting the cluster is
destructive and should only be done after confirming it is no longer
needed. - Do not delete shared IAM roles or VPC resources without
checking whether other resources depend on them. - PostgreSQL currently
uses `emptyDir`; stopping/removing its Pod loses the temporary database
contents.

## 8. Concepts Reviewed

-   Kubernetes Service DNS and inter-Pod communication.
-   Application container `localhost` versus Kubernetes Service names.
-   Kubernetes Secrets and read-only volume mounts.
-   Persistent volumes, StorageClasses, EBS CSI provisioning, and
    topology errors.
-   EKS Auto Mode, node pools, managed node groups, and AMI selection.
-   EKS access entries versus AWS IAM permissions for AWS management
    APIs.
-   Actuator health checks and Kubernetes port forwarding.
-   EKS control-plane and supporting-resource cost considerations.

## 9. Open Items / Next Session

1.  Verify the current status of the application and PostgreSQL
    Deployments and Pods.
2.  Verify the current state of EKS Auto Mode, managed node group, and
    metrics-server.
3.  Complete the AWS shutdown decision and confirm which resources have
    actually been stopped or deleted.
4.  Check whether `thoughtful-blues-goose` still exists.
5.  Resolve durable PostgreSQL storage and validate persistence across
    Pod recreation before relying on the database.
6.  Resume the planned CI/CD work: GitHub Actions → GHCR → AWS OIDC →
    Kubernetes rollout, retaining the existing Git commit SHA image-tag
    convention rather than switching to `latest`.

## Session Outcome

Completed today: - Reviewed EKS cluster configuration and the day's
infrastructure iterations. - Corrected the application datasource URL to
use the PostgreSQL Kubernetes Service. - Restored PostgreSQL using
temporary storage while deferring persistent-volume troubleshooting. -
Created and mounted the RSA Secret required by the application. -
Verified application health with HTTP 200 and Actuator status `UP`. -
Identified the EC2 role's missing `eks:ListClusters` IAM permission. -
Prepared an AWS shutdown and residual-cost review plan.

Still pending: - Confirming whether workloads were scaled to zero. -
Completing or verifying AWS resource shutdown. - Resolving durable
PostgreSQL storage. - Investigating metrics-server Pending status. -
Resuming GitHub Actions-to-EKS deployment integration.
