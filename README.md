# KubeMicroVM

Kubernetes ACK-compatible controllers and kubectl plugin for AWS Lambda MicroVMs.  
Built with **Quarkus**, **JOSDK**, and **GraalVM native image** (Java 25).

## Components

| Module | Description |
|--------|-------------|
| `operator-core` | CRD models, state machine, enums |
| `operator-controller` | JOSDK reconcilers, AWS SDK integration, drift detection |
| `operator-webhook` | Admission webhooks (mutating + validating) |
| `operator-cli` | `kubectl-microvm` plugin (native binary) |

## Custom Resources

- **MicroVM** — maps 1:1 to an AWS Lambda MicroVM instance
- **MicroVMPool** — manages replica scaling of MicroVMs
- **MicroVMTemplate** — reusable spec template
- **MicroVMNetwork** — VPC/subnet/security group configuration

## Quick Start

```bash
# Build
./mvnw package -DskipTests

# Native binary (ARM64)
./mvnw package -pl operator-cli -Pnative -DskipTests

# Helm chart install (from GHCR OCI)
helm install kube-microvm-operator oci://ghcr.io/plasticity-of-cloud/helm/kube-microvm-operator

# With IRSA
helm install kube-microvm-operator oci://ghcr.io/plasticity-of-cloud/helm/kube-microvm-operator \
  --set serviceAccount.irsaRoleArn=arn:aws:iam::<ACCOUNT_ID>:role/<ROLE_NAME>
```

## IAM Configuration

**EKS Pod Identity (recommended)**:
```bash
aws eks create-pod-identity-association \
  --cluster-name <CLUSTER> \
  --namespace kube-microvm \
  --service-account kube-microvm-operator \
  --role-arn arn:aws:iam::<ACCOUNT_ID>:role/<ROLE_NAME>
```

**IRSA**:
```bash
helm upgrade kube-microvm-operator oci://ghcr.io/plasticity-of-cloud/helm/kube-microvm-operator \
  --set serviceAccount.irsaRoleArn=arn:aws:iam::<ACCOUNT_ID>:role/<ROLE_NAME>
```

## Development

```bash
# Run tests
./mvnw verify

# Dev mode (controller)
./mvnw quarkus:dev -pl operator-controller
```

## License

Copyright (c) 2026 Plasticity.Cloud & Codriverlabs

Licensed under the [Express-Compute Community License](LICENSE.md).
