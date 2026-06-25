# Requirements Document

## Introduction

This document defines the requirements for the **Lambda VM ACK Operator** — a Kubernetes operator that manages the lifecycle of AWS Lambda MicroVMs (Firecracker-based) following the AWS Controllers for Kubernetes (ACK) pattern. The operator reconciles Kubernetes custom resources with AWS Lambda MicroVM state, providing declarative management of isolated sandboxes with full lifecycle control (create, start, stop, pause, resume, destroy). A companion `kubectl-microvm` CLI plugin provides imperative user interaction.

The project uses Java 25, Quarkus, the Java Operator SDK (JOSDK) with `quarkus-operator-sdk`, Fabric8 Kubernetes Client, AWS SDK v2, and GraalVM native image compilation. It follows the multi-module Maven structure established by the `eks-d-xpress-control-plane` reference project.

## Glossary

- **Operator**: The Kubernetes controller application that watches MicroVM custom resources and reconciles desired state with AWS
- **MicroVM**: A Firecracker-based isolated virtual machine managed by the AWS Lambda MicroVM service
- **MicroVM_Resource**: The Kubernetes custom resource (CRD) representing a single AWS Lambda MicroVM instance
- **MicroVMPool_Resource**: The Kubernetes custom resource representing a pool of MicroVMs with scaling configuration
- **MicroVMTemplate_Resource**: The Kubernetes custom resource representing a reusable MicroVM configuration template
- **MicroVMNetwork_Resource**: The Kubernetes custom resource representing network configuration for MicroVMs (VPC, subnets, security groups)
- **Reconciler**: The JOSDK-based controller component that watches custom resources and drives them toward desired state
- **CLI_Plugin**: The `kubectl-microvm` command-line plugin built with Picocli, Quarkus, and GraalVM native image
- **CRD**: Custom Resource Definition — the Kubernetes API extension schema for MicroVM resources
- **ACK_Pattern**: AWS Controllers for Kubernetes pattern — maps AWS resource lifecycle to Kubernetes custom resources
- **State_Machine**: The lifecycle state transition model governing MicroVM states (Pending → Creating → Running → Paused → Stopped → Terminating → Terminated)
- **Webhook_Server**: The admission webhook component providing validation and mutation of MicroVM resources
- **Helm_Chart**: The packaging format for deploying the Operator into Kubernetes clusters
- **JOSDK**: Java Operator SDK — the framework providing reconciler lifecycle management, event sources, and dependent resources
- **Finalizer**: A Kubernetes metadata marker that ensures cleanup logic runs before resource deletion
- **IRSA**: IAM Roles for Service Accounts — the mechanism for granting AWS credentials to Kubernetes pods
- **Pod_Identity**: EKS Pod Identity — an alternative mechanism for granting AWS credentials to Kubernetes pods

## Requirements

### Requirement 1: CRD Model Definition

**User Story:** As a platform engineer, I want well-defined Kubernetes Custom Resource Definitions for MicroVMs, so that I can declaratively manage Lambda MicroVM sandboxes using standard Kubernetes tooling.

#### Acceptance Criteria

1. THE Operator SHALL define a namespace-scoped CRD named `microvms.lambda.aws.amazon.com` with API group `lambda.aws.amazon.com` and version `v1alpha1`
2. THE MicroVM_Resource SHALL include a `spec` field containing: `vmId` (optional, AWS-assigned), `runtime` (required, enum: java21, python3.12, nodejs20, custom), `memoryMB` (required, integer 128–10240), `vcpus` (required, integer 1–6), `timeoutSeconds` (optional, integer 1–900, default 300), `networkRef` (optional, reference to MicroVMNetwork_Resource), and `templateRef` (optional, reference to MicroVMTemplate_Resource)
3. THE MicroVM_Resource SHALL include a `status` field containing: `state` (enum matching State_Machine states), `vmId` (AWS-assigned identifier), `ipAddress` (assigned IP when running), `conditions` (standard Kubernetes conditions array), `lastTransitionTime`, and `observedGeneration`
4. THE Operator SHALL define a namespace-scoped CRD named `microvmpools.lambda.aws.amazon.com` with API group `lambda.aws.amazon.com` and version `v1alpha1`
5. THE MicroVMPool_Resource SHALL include a `spec` field containing: `replicas` (required, integer 0–100), `template` (required, embedded MicroVM_Resource spec), `minReady` (optional, integer, default 1), and `maxSurge` (optional, integer, default 1)
6. THE MicroVMPool_Resource SHALL include a `status` field containing: `readyReplicas`, `currentReplicas`, `desiredReplicas`, `conditions`, and `observedGeneration`
7. THE Operator SHALL define a namespace-scoped CRD named `microvmtemplates.lambda.aws.amazon.com` with API group `lambda.aws.amazon.com` and version `v1alpha1`
8. THE MicroVMTemplate_Resource SHALL include a `spec` field containing: `runtime`, `memoryMB`, `vcpus`, `timeoutSeconds`, `environment` (map of key-value pairs), and `labels` (map of key-value pairs applied to created MicroVMs)
9. THE Operator SHALL define a namespace-scoped CRD named `microvmnetworks.lambda.aws.amazon.com` with API group `lambda.aws.amazon.com` and version `v1alpha1`
10. THE MicroVMNetwork_Resource SHALL include a `spec` field containing: `vpcId` (required), `subnetIds` (required, list of 1–6 subnet IDs), and `securityGroupIds` (required, list of 1–5 security group IDs)
11. THE Operator SHALL generate CRD YAML manifests from annotated Java model classes using the Fabric8 CRD generator at build time
12. WHEN a CRD is installed in the cluster, THE Operator SHALL register printer columns for `STATE`, `VM-ID`, `RUNTIME`, and `AGE` so that `kubectl get microvms` displays a human-readable table

### Requirement 2: MicroVM Lifecycle State Machine

**User Story:** As a platform engineer, I want a well-defined lifecycle state machine for MicroVMs, so that I can understand and control the exact state of each sandbox at any time.

#### Acceptance Criteria

1. THE State_Machine SHALL define the following states: `Pending`, `Creating`, `Running`, `Paused`, `Resuming`, `Stopped`, `Starting`, `Stopping`, `Terminating`, `Terminated`, and `Failed`
2. WHEN a MicroVM_Resource is created, THE Reconciler SHALL set the initial state to `Pending`
3. WHEN the state is `Pending`, THE Reconciler SHALL transition to `Creating` and invoke the AWS Lambda MicroVM create API
4. WHEN the AWS create API returns success, THE Reconciler SHALL transition the state from `Creating` to `Running`
5. WHEN the user sets `spec.desiredState` to `paused` while the state is `Running`, THE Reconciler SHALL transition to `Paused` via the AWS pause API
6. WHEN the user sets `spec.desiredState` to `running` while the state is `Paused`, THE Reconciler SHALL transition through `Resuming` to `Running` via the AWS resume API
7. WHEN the user sets `spec.desiredState` to `stopped` while the state is `Running`, THE Reconciler SHALL transition through `Stopping` to `Stopped` via the AWS stop API
8. WHEN the user sets `spec.desiredState` to `running` while the state is `Stopped`, THE Reconciler SHALL transition through `Starting` to `Running` via the AWS start API
9. WHEN the MicroVM_Resource is deleted (deletion timestamp set), THE Reconciler SHALL transition to `Terminating` and invoke the AWS destroy API
10. WHEN the AWS destroy API returns success, THE Reconciler SHALL transition the state to `Terminated` and remove the Finalizer
11. IF an AWS API call fails with a retryable error, THEN THE Reconciler SHALL remain in the current transitional state and requeue the reconciliation with exponential backoff
12. IF an AWS API call fails with a non-retryable error, THEN THE Reconciler SHALL transition to `Failed` and record the error message in `status.conditions`
13. THE Reconciler SHALL reject invalid state transitions by ignoring `spec.desiredState` values that do not correspond to a valid edge from the current state
14. WHILE the state is `Terminating`, THE Reconciler SHALL prevent any other state transition until the AWS destroy completes or times out
15. THE Reconciler SHALL update `status.lastTransitionTime` each time the state changes

### Requirement 3: Reconciliation Loop

**User Story:** As a platform engineer, I want the operator to continuously reconcile declared MicroVM state with actual AWS state, so that drift is automatically detected and corrected.

#### Acceptance Criteria

1. THE Reconciler SHALL implement the JOSDK `Reconciler<MicroVM>` interface and register with the `quarkus-operator-sdk` extension
2. WHEN a MicroVM_Resource is created, updated, or deleted, THE Reconciler SHALL be invoked within 5 seconds of the event
3. THE Reconciler SHALL add a Finalizer named `lambda.aws.amazon.com/microvm-protection` to every MicroVM_Resource during the first reconciliation
4. WHEN reconciliation is triggered, THE Reconciler SHALL query the AWS Lambda MicroVM describe API to detect drift between desired and actual state
5. IF drift is detected between `spec.desiredState` and the actual AWS state, THEN THE Reconciler SHALL invoke the appropriate AWS API to correct the drift
6. THE Reconciler SHALL increment `status.observedGeneration` to match `metadata.generation` after each successful reconciliation
7. WHEN reconciliation completes without error, THE Reconciler SHALL schedule a periodic re-sync after 60 seconds to detect out-of-band changes
8. IF the AWS describe API returns a `ResourceNotFoundException`, THEN THE Reconciler SHALL recreate the MicroVM by transitioning back to `Creating`
9. THE Reconciler SHALL use JOSDK event sources to watch for changes to referenced MicroVMNetwork_Resource and MicroVMTemplate_Resource objects
10. THE Reconciler SHALL update the `Ready` condition in `status.conditions` to reflect the current reconciliation outcome with appropriate reason codes

### Requirement 4: AWS SDK Integration

**User Story:** As a platform engineer, I want secure and configurable AWS SDK integration, so that the operator can authenticate and communicate with AWS Lambda MicroVM APIs across regions.

#### Acceptance Criteria

1. THE Operator SHALL use the AWS SDK v2 default credential provider chain, supporting IRSA, Pod_Identity, environment variables, and instance metadata
2. THE Operator SHALL read the target AWS region from the `AWS_REGION` environment variable, falling back to `us-east-1` if unset
3. WHEN a MicroVM_Resource specifies `spec.region`, THE Operator SHALL use that region for AWS API calls for that specific resource, overriding the default
4. THE Operator SHALL configure the AWS SDK HTTP client with connection timeout of 5 seconds and request timeout of 30 seconds
5. THE Operator SHALL implement retry logic with exponential backoff (base 200ms, max 5 retries) for throttled and transient AWS API errors
6. IF AWS credentials are expired or unavailable, THEN THE Operator SHALL log an error, set the `Ready` condition to `False` with reason `AWSAuthError`, and requeue after 30 seconds
7. THE Operator SHALL use a dedicated `ExecutorService` thread pool (size configurable, default 10) for asynchronous AWS API calls to avoid blocking the reconciler thread

### Requirement 5: Admission Webhooks

**User Story:** As a platform engineer, I want admission webhooks to validate and mutate MicroVM resources at creation time, so that invalid configurations are rejected early and sensible defaults are applied.

#### Acceptance Criteria

1. WHEN a MicroVM_Resource is submitted for creation or update, THE Webhook_Server SHALL validate that `spec.memoryMB` is between 128 and 10240 and is a multiple of 64
2. WHEN a MicroVM_Resource is submitted for creation or update, THE Webhook_Server SHALL validate that `spec.vcpus` is between 1 and 6
3. WHEN a MicroVM_Resource is submitted for creation or update, THE Webhook_Server SHALL validate that `spec.runtime` is one of the allowed enum values
4. WHEN a MicroVM_Resource references a `spec.networkRef`, THE Webhook_Server SHALL validate that the referenced MicroVMNetwork_Resource exists in the same namespace
5. WHEN a MicroVM_Resource is submitted without `spec.timeoutSeconds`, THE Webhook_Server SHALL mutate the resource to set the default value of 300
6. WHEN a MicroVM_Resource is submitted without `spec.memoryMB`, THE Webhook_Server SHALL mutate the resource to set the default value of 512
7. IF validation fails, THEN THE Webhook_Server SHALL return a denial response with a human-readable message listing all validation errors
8. THE Webhook_Server SHALL configure `failurePolicy: Fail` for validating webhooks and `failurePolicy: Ignore` for mutating webhooks
9. THE Webhook_Server SHALL serve webhook endpoints over TLS using certificates mounted from a Kubernetes Secret managed by cert-manager

### Requirement 6: MicroVMPool Scaling

**User Story:** As a platform engineer, I want to manage pools of MicroVMs with replica-based scaling, so that I can maintain a warm pool of sandboxes ready for workloads.

#### Acceptance Criteria

1. WHEN a MicroVMPool_Resource is created, THE Reconciler SHALL create child MicroVM_Resource objects matching the `spec.replicas` count using the embedded template
2. WHEN `spec.replicas` is increased, THE Reconciler SHALL create additional MicroVM_Resource objects up to the desired count, respecting `spec.maxSurge`
3. WHEN `spec.replicas` is decreased, THE Reconciler SHALL delete excess MicroVM_Resource objects, selecting the most recently created first
4. THE Reconciler SHALL label all child MicroVM_Resource objects with `lambda.aws.amazon.com/pool-name` set to the MicroVMPool_Resource name
5. THE Reconciler SHALL set owner references on child MicroVM_Resource objects pointing to the parent MicroVMPool_Resource for garbage collection
6. THE Reconciler SHALL throttle pool scaling operations to create or delete no more than 5 MicroVM_Resource objects per reconciliation cycle
7. THE Reconciler SHALL update `status.readyReplicas` to reflect the count of child MicroVM_Resource objects in `Running` state

### Requirement 7: kubectl Plugin CLI

**User Story:** As a developer, I want a kubectl plugin for managing MicroVMs, so that I can interact with sandboxes using familiar command-line patterns without writing YAML.

#### Acceptance Criteria

1. THE CLI_Plugin SHALL provide a `kubectl microvm create` command that accepts `--runtime`, `--memory`, `--vcpus`, `--timeout`, `--namespace`, and `--name` flags and creates a MicroVM_Resource
2. THE CLI_Plugin SHALL provide a `kubectl microvm list` command that displays MicroVM_Resource objects in a table format with columns: NAME, STATE, VM-ID, RUNTIME, MEMORY, AGE
3. THE CLI_Plugin SHALL provide a `kubectl microvm describe <name>` command that displays the full spec and status of a MicroVM_Resource in human-readable format
4. THE CLI_Plugin SHALL provide a `kubectl microvm delete <name>` command that deletes the specified MicroVM_Resource and waits for termination with a 60-second timeout
5. THE CLI_Plugin SHALL provide a `kubectl microvm pause <name>` command that sets `spec.desiredState` to `paused`
6. THE CLI_Plugin SHALL provide a `kubectl microvm resume <name>` command that sets `spec.desiredState` to `running` from a paused state
7. THE CLI_Plugin SHALL provide a `kubectl microvm stop <name>` command that sets `spec.desiredState` to `stopped`
8. THE CLI_Plugin SHALL provide a `kubectl microvm start <name>` command that sets `spec.desiredState` to `running` from a stopped state
9. THE CLI_Plugin SHALL provide a `kubectl microvm logs <name>` command that streams or tails execution logs from the MicroVM
10. THE CLI_Plugin SHALL provide a `kubectl microvm exec <name> -- <command>` command that sends a command for execution inside the MicroVM and returns the output
11. THE CLI_Plugin SHALL provide a `kubectl microvm pool create` subcommand that accepts `--replicas`, `--runtime`, `--memory`, `--vcpus`, and `--name` flags
12. THE CLI_Plugin SHALL provide a `kubectl microvm pool scale <name> --replicas=N` subcommand that patches the MicroVMPool_Resource replicas field
13. THE CLI_Plugin SHALL use Picocli for argument parsing with automatic help generation and bash/zsh completion script generation
14. IF a referenced MicroVM_Resource does not exist, THEN THE CLI_Plugin SHALL display an error message `Error: MicroVM "<name>" not found in namespace "<namespace>"` and exit with code 1
15. IF the Kubernetes API server is unreachable, THEN THE CLI_Plugin SHALL display an error message `Error: unable to connect to cluster` and exit with code 1
16. THE CLI_Plugin SHALL compile to a GraalVM native image binary named `kubectl-microvm` that starts in under 50 milliseconds

### Requirement 8: Helm Chart Packaging

**User Story:** As a platform engineer, I want a Helm chart for deploying the operator, so that I can install and configure it consistently across clusters with environment-specific values.

#### Acceptance Criteria

1. THE Helm_Chart SHALL include templates for: Deployment, ServiceAccount, ClusterRole, ClusterRoleBinding, Service (webhook), ValidatingWebhookConfiguration, MutatingWebhookConfiguration, and all CRD manifests
2. THE Helm_Chart SHALL expose configurable values for: `replicaCount`, `image.repository`, `image.tag`, `resources.requests`, `resources.limits`, `aws.region`, `aws.irsaRoleArn`, `webhook.certManager.enabled`, and `metrics.enabled`
3. THE Helm_Chart SHALL include a `values.yaml` with sensible defaults: 1 replica, 256Mi memory request, 512Mi memory limit, 100m CPU request, 500m CPU limit
4. WHEN `webhook.certManager.enabled` is true, THE Helm_Chart SHALL include a cert-manager Certificate resource for webhook TLS
5. THE Helm_Chart SHALL include NOTES.txt providing post-install instructions including how to verify the operator is running and how to create a first MicroVM
6. THE Helm_Chart SHALL pass `helm lint --strict` without errors or warnings
7. THE Helm_Chart SHALL support Kubernetes versions 1.27 through 1.32 as declared in `Chart.yaml` `kubeVersion` field
8. THE Helm_Chart SHALL include a `crds/` directory with all CRD manifests installed before other resources per Helm CRD conventions

### Requirement 9: Health and Observability

**User Story:** As an SRE, I want comprehensive health checks and observability, so that I can monitor the operator's health, diagnose issues, and set up alerting.

#### Acceptance Criteria

1. THE Operator SHALL expose a `/healthz` endpoint that returns HTTP 200 when the operator is healthy and HTTP 503 when unhealthy
2. THE Operator SHALL expose a `/readyz` endpoint that returns HTTP 200 only when the operator has completed leader election and established a watch connection to the Kubernetes API
3. THE Operator SHALL expose Prometheus metrics at `/metrics` including: `microvm_reconciliations_total` (counter by outcome), `microvm_reconciliation_duration_seconds` (histogram), `microvm_state_transitions_total` (counter by from_state and to_state), and `microvm_aws_api_calls_total` (counter by operation and status)
4. THE Operator SHALL emit structured JSON log entries containing: timestamp, level, logger name, reconciliation resource namespace/name, correlation ID, and message
5. WHEN a state transition occurs, THE Operator SHALL emit a Kubernetes Event on the MicroVM_Resource with reason matching the transition (e.g., `Created`, `Started`, `Paused`, `Failed`)
6. THE Operator SHALL include a `ServiceMonitor` resource in the Helm chart for Prometheus Operator integration when `metrics.enabled` is true
7. THE Operator SHALL set Quarkus log level to INFO by default with DEBUG available via the `QUARKUS_LOG_LEVEL` environment variable

### Requirement 10: Security and Multi-Tenancy

**User Story:** As a security engineer, I want namespace-scoped isolation and least-privilege access controls, so that tenants cannot interfere with each other's MicroVM resources.

#### Acceptance Criteria

1. THE Operator SHALL enforce namespace isolation such that a MicroVM_Resource in namespace A cannot reference a MicroVMNetwork_Resource in namespace B
2. THE Operator SHALL define a ClusterRole with minimum required permissions: get/list/watch/create/update/patch/delete on MicroVM CRDs, get/list on Secrets (for webhook certs), and create on Events
3. WHEN a namespace has a ResourceQuota with `count/microvms.lambda.aws.amazon.com` set, THE Operator SHALL respect that quota and reject creation beyond the limit via the admission webhook
4. THE Operator SHALL support configurable RBAC with separate ClusterRoles for `microvm-admin` (full CRUD), `microvm-editor` (create, update, delete own resources), and `microvm-viewer` (read-only)
5. THE Webhook_Server SHALL validate that the requesting user has the `lambda.aws.amazon.com/manage-vms` permission annotation on their namespace before allowing MicroVM creation
6. THE Operator SHALL encrypt sensitive fields in MicroVM_Resource status (such as temporary credentials) using Kubernetes Secret references rather than inline values
7. THE Operator SHALL add network policy recommendations in the Helm chart to restrict operator pod egress to the Kubernetes API server and AWS API endpoints only
8. THE Operator SHALL support running in a dedicated operator namespace separate from tenant namespaces, watching all namespaces or a configurable list
9. THE Webhook_Server SHALL serve all endpoints exclusively over TLS with minimum TLS 1.2 and reject non-TLS connections

### Requirement 11: GraalVM Native Image

**User Story:** As a platform engineer, I want the operator and CLI plugin compiled as GraalVM native images, so that they start instantly and use minimal memory suitable for edge and resource-constrained environments.

#### Acceptance Criteria

1. THE Operator SHALL compile to a GraalVM native image using the `quarkus.native.enabled=true` build profile
2. WHEN built with the native profile, THE Operator SHALL start and be ready to accept reconciliation events within 1 second
3. THE Operator SHALL include GraalVM reflection configuration for all CRD model classes, AWS SDK classes, and Fabric8 model classes required at runtime
4. WHEN built with the native profile, THE Operator SHALL produce a statically linked binary compatible with `distroless` or `scratch` container base images
5. THE CLI_Plugin SHALL compile to a GraalVM native image that starts and executes simple commands (e.g., `--help`) in under 50 milliseconds
6. IF a class requires runtime reflection not detectable at build time, THEN THE Operator SHALL declare it in `reflect-config.json` or via `@RegisterForReflection` annotation
7. THE Operator SHALL include a JVM-mode build profile (`-Pjvm`) as a fallback for development and debugging where native image is not required
8. THE Operator SHALL run the full integration test suite against the native image binary in CI to ensure functional parity with JVM mode

### Requirement 12: Project Structure

**User Story:** As a developer, I want a well-organized multi-module Maven project, so that modules are independently buildable, testable, and have clear dependency boundaries.

#### Acceptance Criteria

1. THE Operator SHALL be organized as a multi-module Maven project with the following modules: `operator-core` (CRD models, state machine logic), `operator-controller` (reconcilers, event sources), `operator-webhook` (admission webhook server), `operator-cli` (kubectl plugin), `operator-tests` (integration tests), and a parent POM
2. THE `operator-core` module SHALL have zero dependencies on Kubernetes client libraries, containing only CRD model POJOs, enums, and the state machine definition
3. THE `operator-controller` module SHALL depend on `operator-core`, JOSDK, Fabric8, and AWS SDK v2
4. THE `operator-webhook` module SHALL depend on `operator-core` and Quarkus REST for serving webhook endpoints
5. THE `operator-cli` module SHALL depend on `operator-core`, Picocli, and the Fabric8 Kubernetes client
6. THE `operator-tests` module SHALL depend on all other modules and include integration tests using the JOSDK test framework and Testcontainers
7. WHEN any single module is built in isolation with `mvn -pl <module> package`, THE build SHALL succeed without requiring other modules to be pre-installed in the local repository (using reactor build order)
8. THE parent POM SHALL enforce consistent dependency versions using a `<dependencyManagement>` section with BOM imports for Quarkus, AWS SDK v2, and Fabric8
9. THE parent POM SHALL configure the `maven-enforcer-plugin` to reject duplicate dependencies and ensure minimum Maven version 3.9.0

### Requirement 13: CRD Serialization and Parsing

**User Story:** As a developer, I want robust serialization and deserialization of CRD objects, so that data integrity is maintained across API server interactions and controller restarts.

#### Acceptance Criteria

1. FOR ALL valid MicroVM_Resource objects, serializing to JSON then deserializing SHALL produce an object equal to the original (round-trip property)
2. FOR ALL valid MicroVMPool_Resource objects, serializing to JSON then deserializing SHALL produce an object equal to the original (round-trip property)
3. WHEN a MicroVM_Resource contains unknown fields not defined in the CRD schema, THE Operator SHALL preserve those fields through serialization round-trips using `@JsonAnyGetter`/`@JsonAnySetter`
4. THE Operator SHALL use Jackson with the Fabric8 Kubernetes serialization module for all CRD serialization and deserialization
5. WHEN a MicroVM_Resource is deserialized with a missing optional field, THE Operator SHALL apply the defined default value rather than null
6. FOR ALL valid MicroVMTemplate_Resource and MicroVMNetwork_Resource objects, serializing to YAML then deserializing SHALL produce an object equal to the original (round-trip property)
7. THE CRD model classes SHALL implement `equals()`, `hashCode()`, and `toString()` methods to support round-trip equality assertions and debugging

### Requirement 14: Container Image Building

**User Story:** As a DevOps engineer, I want automated multi-architecture container image builds, so that the operator runs on both AMD64 and ARM64 Kubernetes nodes with minimal image size.

#### Acceptance Criteria

1. THE Operator SHALL use a multi-stage Dockerfile with a build stage (GraalVM CE or Mandrel builder image) and a runtime stage (`distroless/static` or equivalent minimal base)
2. THE Operator container image SHALL support both `linux/amd64` and `linux/arm64` architectures published as a multi-arch manifest
3. WHEN built with the native profile, THE Operator container image SHALL be less than 100MB in size for the runtime layer
4. THE Operator SHALL use Quarkus container-image-jib or container-image-docker extension for image building integrated into the Maven build lifecycle
5. THE Operator container image SHALL include OCI labels for: `org.opencontainers.image.source`, `org.opencontainers.image.version`, `org.opencontainers.image.revision`, and `org.opencontainers.image.created`
6. THE CLI_Plugin SHALL produce a standalone container image containing only the `kubectl-microvm` native binary and a shell for use in CI/CD pipelines
