# Implementation Plan: Lambda VM ACK Operator

## Overview

This plan implements the Lambda VM ACK Operator as a multi-module Maven project (Java 25, Quarkus 3.36.x, JOSDK, Fabric8, AWS SDK v2). Tasks progress from foundational models through reconcilers, webhooks, CLI, Helm chart, and integration tests. Each module builds incrementally, with property-based tests (jqwik 1.9.x) validating correctness properties alongside implementation.

## Tasks

- [ ] 1. Set up project structure and parent POM
  - [ ] 1.1 Create parent POM with multi-module reactor and dependency management
    - Create `pom.xml` at project root with `<modules>`: operator-core, operator-controller, operator-webhook, operator-cli, operator-tests
    - Add `<dependencyManagement>` with BOM imports for Quarkus 3.36.x, AWS SDK v2, Fabric8 Kubernetes Client
    - Configure `maven-enforcer-plugin` to reject duplicate dependencies and enforce Maven >= 3.9.0
    - Define properties for Java 25, jqwik 1.9.x, Picocli, JOSDK versions
    - Add build profiles: `jvm` (default), `native` (GraalVM native image)
    - _Requirements: 12.7, 12.8, 12.9_


  - [ ] 1.2 Create operator-core module POM and directory structure
    - Create `operator-core/pom.xml` with zero Kubernetes/framework dependencies
    - Add dependencies: Jackson (databind, annotations), jqwik (test scope)
    - Create standard Maven directory layout: `src/main/java`, `src/test/java`
    - Create package structure: `com.amazonaws.lambda.operator.core.model`, `.state`, `.enums`, `.validation`
    - _Requirements: 12.1, 12.2_

  - [ ] 1.3 Create operator-controller module POM and directory structure
    - Create `operator-controller/pom.xml` depending on operator-core
    - Add dependencies: JOSDK (`quarkus-operator-sdk`), Fabric8 client, AWS SDK v2 (Lambda, STS), Micrometer
    - Create package structure: `com.amazonaws.lambda.operator.controller.reconciler`, `.aws`, `.config`, `.metrics`
    - _Requirements: 12.1, 12.3_

  - [ ] 1.4 Create operator-webhook module POM and directory structure
    - Create `operator-webhook/pom.xml` depending on operator-core
    - Add dependencies: Quarkus REST (RESTEasy Reactive), Jackson, Fabric8 client
    - Create package structure: `com.amazonaws.lambda.operator.webhook.validation`, `.mutation`
    - _Requirements: 12.1, 12.4_

  - [ ] 1.5 Create operator-cli module POM and directory structure
    - Create `operator-cli/pom.xml` depending on operator-core
    - Add dependencies: Picocli, Quarkus Picocli extension, Fabric8 Kubernetes client
    - Create package structure: `com.amazonaws.lambda.operator.cli.commands`, `.output`
    - _Requirements: 12.1, 12.5_

  - [ ] 1.6 Create operator-tests module POM and directory structure
    - Create `operator-tests/pom.xml` depending on all other modules
    - Add dependencies: QuarkusTest, JOSDK test framework, Fabric8 kubernetes-server-mock, Testcontainers, jqwik
    - Create package structure: `com.amazonaws.lambda.operator.tests.integration`, `.properties`
    - _Requirements: 12.1, 12.6_


- [ ] 2. Implement operator-core CRD models and enums
  - [ ] 2.1 Implement enums: Runtime, DesiredState, MicroVMState
    - Create `Runtime` enum with `@JsonValue`/`@JsonCreator` for JSON serialization (java21, python3.12, nodejs20, custom)
    - Create `DesiredState` enum (RUNNING, PAUSED, STOPPED)
    - Create `MicroVMState` enum with all 11 lifecycle states (Pending through Terminated and Failed)
    - _Requirements: 1.2, 2.1_

  - [ ] 2.2 Implement MicroVMSpec and MicroVMStatus model classes
    - Create `MicroVMSpec` with all fields: vmId, runtime, memoryMB, vcpus, timeoutSeconds, networkRef, templateRef, desiredState, region, tags
    - Annotate with `@JsonInclude(NON_NULL)`, `@JsonAnySetter`/`@JsonAnyGetter` for unknown field preservation
    - Create `MicroVMStatus` with: state, vmId, ipAddress, conditions list, lastTransitionTime, observedGeneration
    - Create `Condition` class matching Kubernetes condition schema (type, status, reason, message, lastTransitionTime)
    - Implement `equals()`, `hashCode()`, `toString()` on all model classes
    - _Requirements: 1.2, 1.3, 13.3, 13.7_

  - [ ] 2.3 Implement MicroVM CustomResource class
    - Create `MicroVM` extending `CustomResource<MicroVMSpec, MicroVMStatus>` implementing `Namespaced`
    - Annotate with `@Group("lambda.aws.amazon.com")`, `@Version("v1alpha1")`, `@Kind("MicroVM")`, `@Plural("microvms")`, `@ShortNames("mvm")`
    - Add printer column annotations for STATE, VM-ID, RUNTIME, AGE
    - _Requirements: 1.1, 1.12_


  - [ ] 2.4 Implement MicroVMPool, MicroVMTemplate, MicroVMNetwork CRD classes
    - Create `MicroVMPoolSpec` (replicas, template, minReady, maxSurge) and `MicroVMPoolStatus` (readyReplicas, currentReplicas, desiredReplicas, conditions, observedGeneration)
    - Create `MicroVMPool` extending `CustomResource<MicroVMPoolSpec, MicroVMPoolStatus>` with group/version annotations
    - Create `MicroVMTemplateSpec` (runtime, memoryMB, vcpus, timeoutSeconds, environment, labels) and `MicroVMTemplate` CR class
    - Create `MicroVMNetworkSpec` (vpcId, subnetIds, securityGroupIds) and `MicroVMNetwork` CR class
    - Implement `equals()`, `hashCode()`, `toString()` on all spec/status classes
    - _Requirements: 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 13.7_

  - [ ] 2.5 Implement MicroVMStateMachine with sealed interface result type
    - Create `sealed interface StateTransitionResult` with `record Valid(MicroVMState from, MicroVMState to)` and `record Invalid(MicroVMState from, MicroVMState attemptedTo, String reason)`
    - Create `MicroVMStateMachine` class with `VALID_TRANSITIONS` map defining all legal edges
    - Implement `transition(MicroVMState current, MicroVMState target)` returning appropriate result
    - Implement `validTargets(MicroVMState current)` returning the set of valid next states
    - Ensure Terminating state blocks all non-Terminated transitions
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.13, 2.14_

  - [ ]* 2.6 Write property test: CRD Serialization Round Trip (Property 1)
    - **Property 1: CRD Serialization Round Trip**
    - **Validates: Requirements 1.2, 1.3, 1.5, 1.6, 1.8, 1.10, 13.1, 13.2, 13.6**
    - Create `CrdSerializationProperties` test class in operator-core
    - Implement `@Provide` Arbitrary combinators for MicroVM, MicroVMPool, MicroVMTemplate, MicroVMNetwork
    - Write `@Property(tries = 200)` method: serialize to JSON, deserialize, assert equals original
    - Write YAML round-trip variant for Template and Network resources


  - [ ]* 2.7 Write property test: State Transition Validity (Property 2)
    - **Property 2: State Transition Validity**
    - **Validates: Requirements 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.14**
    - Create `StateMachineProperties` test class in operator-core
    - Implement `ValidTransitionArbitrary` generating pairs from defined valid edge set
    - Write `@Property(tries = 100)` asserting `transition()` returns `Valid` for all legal edges

  - [ ]* 2.8 Write property test: Invalid State Transitions Rejected (Property 3)
    - **Property 3: Invalid State Transitions Are Rejected**
    - **Validates: Requirements 2.13, 2.14**
    - Implement `InvalidTransitionArbitrary` generating pairs from complement of valid edges
    - Write `@Property(tries = 100)` asserting `transition()` returns `Invalid` with non-empty reason for all illegal edges

  - [ ]* 2.9 Write property test: Unknown Fields Preserved (Property 10)
    - **Property 10: Unknown Fields Ignored on Deserialization**
    - **Validates: Requirements 13.3**
    - In `CrdSerializationProperties`, add property test that augments MicroVM JSON with arbitrary unknown fields
    - Assert re-serialization preserves all unknown fields via `@JsonAnySetter`/`@JsonAnyGetter`

- [ ] 3. Checkpoint - Core module verification
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 4. Implement operator-controller module
  - [ ] 4.1 Implement MicroVMClient interface and AWS SDK CDI producer
    - Create `MicroVMClient` interface with async methods: createMicroVM, describeMicroVM, startMicroVM, stopMicroVM, pauseMicroVM, resumeMicroVM, destroyMicroVM
    - Create `CreateMicroVMRequest`, `CreateMicroVMResponse`, `DescribeMicroVMResponse` DTOs
    - Create `AwsClientProducer` CDI bean with `@Produces @ApplicationScoped` for default async client
    - Configure connection timeout (5s), request timeout (30s), retry policy (base 200ms, max 5 retries, equal-jitter backoff)
    - Implement region-aware client producer with `@Dependent` scope for per-resource region override
    - Read region from `AWS_REGION` env var with fallback to `us-east-1`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.7_

  - [ ] 4.2 Implement MicroVMClient concrete implementation wrapping AWS SDK
    - Create `DefaultMicroVMClient` implementing `MicroVMClient`
    - Map CRD spec fields to AWS API request objects
    - Propagate `spec.tags` map to AWS CreateMicroVM request without modification
    - Implement error classification: retryable (429, 5xx, timeout) vs non-retryable (400, 403)
    - Handle credential expiry: catch StsException, signal AWSAuthError
    - Use dedicated `ExecutorService` thread pool (configurable size, default 10) for async calls
    - _Requirements: 4.1, 4.4, 4.5, 4.6, 4.7_


  - [ ] 4.3 Implement DriftDetector for desired vs actual state comparison
    - Create `DriftDetector` class that compares `spec.desiredState` against actual AWS state
    - Implement `detectDrift(DesiredState desired, MicroVMState actual)` returning drift action or no-op
    - Map drift to correct AWS API operation (start, stop, pause, resume, create)
    - Handle case where no valid transition path exists (signal error)
    - _Requirements: 3.4, 3.5_

  - [ ] 4.4 Implement MicroVMReconciler with JOSDK annotations
    - Create `MicroVMReconciler` implementing `Reconciler<MicroVM>` and `Cleaner<MicroVM>`
    - Annotate with `@ControllerConfiguration`: WATCH_ALL_NAMESPACES, finalizerName, retry config (5 attempts, 200ms base, 2x multiplier)
    - Inject `MicroVMClient`, `MicroVMStateMachine`, `MeterRegistry`
    - Implement `reconcile()`: add finalizer → describe AWS state → detect drift → execute transition → update status → schedule re-sync (60s)
    - Implement `cleanup()`: transition to Terminating → call AWS destroy → return DeleteControl
    - Handle ResourceNotFoundException by transitioning back to Creating
    - Update `status.observedGeneration` to match `metadata.generation` on success
    - Update `status.lastTransitionTime` on each state change
    - Set Ready condition with appropriate reason codes (AWSAuthError, etc.)
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.15, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.10_

  - [ ] 4.5 Implement Kubernetes Event emission and metrics
    - Emit Kubernetes Events on MicroVM resource for each state transition (Created, Started, Paused, Stopped, Failed, etc.)
    - Register Prometheus metrics: `microvm_reconciliations_total` (counter by outcome), `microvm_reconciliation_duration_seconds` (histogram)
    - Register: `microvm_state_transitions_total` (counter by from_state/to_state), `microvm_aws_api_calls_total` (counter by operation/status)
    - _Requirements: 9.3, 9.5_


  - [ ] 4.6 Implement MicroVMPoolReconciler with scaling logic
    - Create `MicroVMPoolReconciler` implementing `Reconciler<MicroVMPool>` with `@ControllerConfiguration`
    - List child MicroVMs by owner reference and pool-name label
    - Scale up: create MicroVMs from template up to desired replicas (max 5 per cycle, respecting maxSurge)
    - Scale down: delete most-recently-created first (max 5 per cycle)
    - Set owner references on child MicroVM resources for garbage collection
    - Label all children with `lambda.aws.amazon.com/pool-name`
    - Update `status.readyReplicas` (count of Running children), `currentReplicas`, `desiredReplicas`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

  - [ ] 4.7 Implement event sources for referenced resources
    - Configure JOSDK event sources to watch MicroVMNetwork and MicroVMTemplate changes
    - Trigger re-reconciliation of MicroVMs referencing the changed Network/Template
    - _Requirements: 3.9_

  - [ ]* 4.8 Write property test: Pool Scaling Invariant (Property 6)
    - **Property 6: Pool Scaling Invariant**
    - **Validates: Requirements 6.1, 6.2, 6.4, 6.5, 6.6, 6.7**
    - Create `PoolScalingProperties` test class in operator-controller
    - Implement `MicroVMPoolArbitrary` and `ChildListArbitrary` for generating test pools
    - Assert: child count in [0, N+S], all children labeled, all children have owner ref, readyReplicas equals Running count

  - [ ]* 4.9 Write property test: Pool Scale-Down Order (Property 7)
    - **Property 7: Pool Scale-Down Order**
    - **Validates: Requirements 6.3**
    - Implement `TimestampedChildListArbitrary` generating children with varied creation timestamps
    - Assert: when scaling down N→M, the (N-M) deleted children have the most recent creationTimestamps


  - [ ]* 4.10 Write property test: Tag Propagation Completeness (Property 11)
    - **Property 11: Tag Propagation Completeness**
    - **Validates: Requirements 4.3, 6.4**
    - Create `TagPropagationProperties` test class in operator-controller
    - Generate arbitrary tag maps with `Arbitraries.maps(strings, strings)`
    - Assert: CreateMicroVM request contains every tag from spec.tags without modification

  - [ ]* 4.11 Write property test: Drift Detection Correctness (Property 12)
    - **Property 12: Drift Detection Correctness**
    - **Validates: Requirements 3.4, 3.5**
    - Create `DriftDetectionProperties` test class in operator-controller
    - Implement `DesiredActualStatePairArbitrary` generating mismatched state pairs
    - Assert: drift detected when desired != actual and valid path exists; error signaled when no path exists

- [ ] 5. Checkpoint - Controller module verification
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 6. Implement operator-webhook module
  - [ ] 6.1 Implement MicroVMValidatingWebhook endpoint
    - Create `MicroVMValidatingWebhook` JAX-RS resource at `/validate-microvm`
    - Validate `spec.memoryMB`: 128–10240 range AND multiple of 64
    - Validate `spec.vcpus`: 1–6 range
    - Validate `spec.runtime`: must match defined enum values
    - Validate `spec.timeoutSeconds` (when present): 1–900 range
    - Validate `spec.networkRef`: check referenced MicroVMNetwork exists in same namespace
    - Validate namespace quota: check `count/microvms.lambda.aws.amazon.com` ResourceQuota
    - Validate namespace permission annotation: `lambda.aws.amazon.com/manage-vms`
    - Aggregate all validation errors into single denial response
    - Configure `failurePolicy: Fail` for the validating webhook
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.7, 5.8, 10.1, 10.3, 10.5_

  - [ ] 6.2 Implement MicroVMMutatingWebhook endpoint
    - Create `MicroVMMutatingWebhook` JAX-RS resource at `/mutate-microvm`
    - Apply default `timeoutSeconds = 300` when null
    - Apply default `memoryMB = 512` when null
    - Generate JSON Patch operations for each default applied
    - Configure `failurePolicy: Ignore` for the mutating webhook
    - _Requirements: 5.5, 5.6, 5.8_

  - [ ] 6.3 Configure TLS and webhook server
    - Configure webhook endpoints to serve exclusively over TLS (minimum TLS 1.2)
    - Mount TLS certificates from Kubernetes Secret managed by cert-manager
    - Reject non-TLS connections
    - _Requirements: 5.9, 10.9_


  - [ ]* 6.4 Write property test: Webhook Range Validation (Property 4)
    - **Property 4: Webhook Range Validation**
    - **Validates: Requirements 5.1, 5.2, 5.3**
    - Create `WebhookValidationProperties` test class in operator-webhook
    - Generate arbitrary integers for memoryMB, vcpus, timeoutSeconds and strings for runtime
    - Assert: webhook accepts iff memoryMB in [128,10240] AND memoryMB%64==0; vcpus in [1,6]; runtime is valid enum; timeoutSeconds in [1,900]

  - [ ]* 6.5 Write property test: Webhook Mutation Defaults (Property 5)
    - **Property 5: Webhook Mutation Applies Defaults**
    - **Validates: Requirements 5.5, 5.6**
    - Create `WebhookMutationProperties` test class in operator-webhook
    - Generate `MicroVMSpec` with null timeoutSeconds and/or null memoryMB
    - Assert: after mutation, timeoutSeconds==300 when was null, memoryMB==512 when was null, all other fields unchanged

  - [ ]* 6.6 Write property test: Namespace Quota Enforcement (Property 8)
    - **Property 8: Namespace Quota Enforcement**
    - **Validates: Requirements 10.3**
    - Create `QuotaEnforcementProperties` test class in operator-webhook
    - Generate arbitrary quota Q and current count C with `Arbitraries.integers().between(0, 200)`
    - Assert: creation rejected iff C >= Q


- [ ] 7. Implement operator-cli module
  - [ ] 7.1 Implement root command and Quarkus Picocli integration
    - Create `MicroVMCommand` root command with `@TopCommand`, `@QuarkusMain`
    - Configure `mixinStandardHelpOptions = true` for automatic `--help`/`--version`
    - Register all subcommands: CreateCommand, ListCommand, DescribeCommand, DeleteCommand, PauseCommand, ResumeCommand, StopCommand, StartCommand, LogsCommand, ExecCommand, PoolCommand
    - Implement bash/zsh completion script generation
    - _Requirements: 7.13_

  - [ ] 7.2 Implement CRUD commands: create, list, describe, delete
    - Create `CreateCommand` with flags: `--runtime` (required), `--memory` (default 512), `--vcpus` (default 2), `--timeout` (default 300), `--namespace` (default "default"), `--name` (required)
    - Create `ListCommand` displaying table: NAME, STATE, VM-ID, RUNTIME, MEMORY, AGE
    - Create `DescribeCommand` accepting `<name>` argument, displaying full spec and status
    - Create `DeleteCommand` accepting `<name>`, deleting resource and waiting for termination (60s timeout)
    - Handle not-found error: `Error: MicroVM "<name>" not found in namespace "<namespace>"` with exit code 1
    - Handle API unreachable: `Error: unable to connect to cluster` with exit code 1
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.14, 7.15_

  - [ ] 7.3 Implement lifecycle commands: pause, resume, stop, start
    - Create `PauseCommand` patching `spec.desiredState` to `paused`
    - Create `ResumeCommand` patching `spec.desiredState` to `running` (from paused)
    - Create `StopCommand` patching `spec.desiredState` to `stopped`
    - Create `StartCommand` patching `spec.desiredState` to `running` (from stopped)
    - _Requirements: 7.5, 7.6, 7.7, 7.8_


  - [ ] 7.4 Implement advanced commands: logs, exec, pool
    - Create `LogsCommand` accepting `<name>`, streaming/tailing execution logs from the MicroVM
    - Create `ExecCommand` accepting `<name> -- <command>`, sending command for execution inside MicroVM, returning output
    - Create `PoolCommand` with subcommands: `pool create` (flags: --replicas, --runtime, --memory, --vcpus, --name) and `pool scale <name> --replicas=N`
    - _Requirements: 7.9, 7.10, 7.11, 7.12_

  - [ ]* 7.5 Write property test: CLI Output Format Consistency (Property 9)
    - **Property 9: CLI Output Format Consistency**
    - **Validates: Requirements 7.2**
    - Create `CliOutputProperties` test class in operator-cli
    - Generate arbitrary MicroVM resources with populated spec and status fields
    - Assert: list output contains row with columns NAME, STATE, VM-ID, RUNTIME, MEMORY, AGE matching resource fields

- [ ] 8. Checkpoint - All module implementations verified
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 9. Implement health endpoints and observability configuration
  - [ ] 9.1 Implement health and readiness endpoints
    - Configure `/healthz` endpoint returning 200 (healthy) or 503 (unhealthy)
    - Configure `/readyz` endpoint returning 200 only when leader election completed and K8s API watch established
    - Configure structured JSON logging: timestamp, level, logger, namespace/name, correlation ID, message
    - Set default log level to INFO, configurable via `QUARKUS_LOG_LEVEL` env var
    - _Requirements: 9.1, 9.2, 9.4, 9.7_

  - [ ] 9.2 Implement RBAC ClusterRoles and security configuration
    - Define ClusterRole with minimum permissions: get/list/watch/create/update/patch/delete on MicroVM CRDs, get/list on Secrets, create on Events
    - Define `microvm-admin` ClusterRole (full CRUD)
    - Define `microvm-editor` ClusterRole (create, update, delete own resources)
    - Define `microvm-viewer` ClusterRole (read-only)
    - Implement namespace isolation: reject cross-namespace references in MicroVMNetwork
    - Encrypt sensitive status fields via Kubernetes Secret references
    - _Requirements: 10.1, 10.2, 10.4, 10.6_


- [ ] 10. Implement Helm chart packaging
  - [ ] 10.1 Create Helm chart structure and Chart.yaml
    - Create `charts/kube-microvm-operator/Chart.yaml` with apiVersion v2, kubeVersion `>=1.27.0-0 <1.33.0-0`
    - Create `values.yaml` with defaults: replicaCount=1, 256Mi memory request, 512Mi limit, 100m CPU request, 500m CPU limit
    - Add configurable values: `image.repository`, `image.tag`, `aws.region`, `aws.irsaRoleArn`, `webhook.certManager.enabled`, `metrics.enabled`
    - Create `templates/_helpers.tpl` with standard Helm template helpers
    - _Requirements: 8.2, 8.3, 8.7_

  - [ ] 10.2 Create CRD manifests and core templates
    - Create `crds/` directory with generated CRD YAML manifests for all 4 CRDs (microvms, microvmpools, microvmtemplates, microvmnetworks)
    - Create `templates/deployment.yaml` for operator Deployment
    - Create `templates/serviceaccount.yaml` with IRSA annotation support
    - Create `templates/clusterrole.yaml` and `templates/clusterrolebinding.yaml`
    - Create `templates/role-admin.yaml`, `templates/role-editor.yaml`, `templates/role-viewer.yaml`
    - _Requirements: 8.1, 8.8_

  - [ ] 10.3 Create webhook and conditional templates
    - Create `templates/service-webhook.yaml` for webhook Service
    - Create `templates/validatingwebhookcfg.yaml` and `templates/mutatingwebhookcfg.yaml`
    - Create `templates/certificate.yaml` (conditional on `webhook.certManager.enabled`)
    - Create `templates/servicemonitor.yaml` (conditional on `metrics.enabled`)
    - Create `templates/networkpolicy.yaml` restricting egress to K8s API and AWS endpoints
    - Create `templates/NOTES.txt` with post-install instructions
    - Create `tests/test-connection.yaml` for helm test
    - Verify chart passes `helm lint --strict`
    - _Requirements: 8.1, 8.4, 8.5, 8.6, 10.7, 10.8_


- [ ] 11. Implement GraalVM native image and container builds
  - [ ] 11.1 Configure GraalVM native image build for operator
    - Add `quarkus.native.enabled=true` in native build profile
    - Create/update `reflect-config.json` for CRD model classes, AWS SDK classes, Fabric8 models
    - Add `@RegisterForReflection` annotations where needed for runtime reflection
    - Configure static linking for distroless/scratch container compatibility
    - Verify operator starts and accepts reconciliation events within 1 second in native mode
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.6, 11.7_

  - [ ] 11.2 Configure GraalVM native image build for CLI plugin
    - Configure native image build for operator-cli producing `kubectl-microvm` binary
    - Verify CLI starts and executes `--help` in under 50 milliseconds
    - _Requirements: 7.16, 11.5_

  - [ ] 11.3 Create multi-architecture Dockerfile and container image configuration
    - Create multi-stage Dockerfile: build stage (GraalVM/Mandrel builder), runtime stage (distroless/static)
    - Configure multi-arch build for `linux/amd64` and `linux/arm64`
    - Add OCI labels: `org.opencontainers.image.source`, `.version`, `.revision`, `.created`
    - Ensure runtime image < 100MB
    - Configure Quarkus container-image-jib or container-image-docker extension in Maven
    - Create standalone CLI container image with `kubectl-microvm` binary and shell
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6_


- [ ] 12. Implement integration tests (operator-tests module)
  - [ ] 12.1 Write integration tests for MicroVM reconciliation lifecycle
    - Create `MicroVMReconcilerIT` using `@QuarkusTest` and `@WithKubernetesTestServer`
    - Test: Pending → Creating → Running with mocked AWS success
    - Test: AWS throttling handling (TooManyRequestsException, state unchanged, requeue)
    - Test: Drift detection and correction (Running but AWS shows Stopped → triggers start)
    - Test: Deletion flow (Terminating → Terminated → finalizer removed)
    - Test: ResourceNotFoundException triggers recreate (→ Creating)
    - Test: Non-retryable error transitions to Failed
    - _Requirements: 2.2, 2.3, 2.4, 2.9, 2.10, 2.11, 2.12, 3.4, 3.5, 3.8_

  - [ ] 12.2 Write integration tests for MicroVMPool scaling
    - Create `MicroVMPoolReconcilerIT`
    - Test: Pool creation spawns correct number of child MicroVMs
    - Test: Scale up creates additional VMs respecting maxSurge
    - Test: Scale down deletes most-recently-created VMs first
    - Test: Owner references and labels set correctly on children
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [ ] 12.3 Write integration tests for webhooks
    - Create `WebhookIT`
    - Test: Invalid memoryMB (out of range, not multiple of 64) rejected
    - Test: Invalid vcpus rejected
    - Test: Invalid runtime rejected
    - Test: Missing optional fields get defaults applied via mutation
    - Test: Cross-namespace network reference rejected
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 10.1_

  - [ ]* 12.4 Write integration test for native image functional parity
    - Configure `native-tests` profile to run full integration suite against native binary
    - Verify functional parity between JVM and native image modes
    - _Requirements: 11.8_

- [ ] 13. Final checkpoint - Full test suite verification
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests (jqwik 1.9.x) validate the 12 correctness properties from the design document
- Unit tests complement property tests for edge cases and specific examples
- Integration tests use `@QuarkusTest` + Fabric8 kubernetes-server-mock
- All modules follow the sealed-interface state machine pattern for compile-time safety
- The build uses Java 25 with Quarkus 3.36.x throughout



## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4", "1.5", "1.6"] },
    { "id": 2, "tasks": ["2.1"] },
    { "id": 3, "tasks": ["2.2", "2.5"] },
    { "id": 4, "tasks": ["2.3", "2.4"] },
    { "id": 5, "tasks": ["2.6", "2.7", "2.8", "2.9"] },
    { "id": 6, "tasks": ["4.1", "6.1", "6.2", "6.3", "7.1"] },
    { "id": 7, "tasks": ["4.2", "4.3", "7.2", "7.3", "7.4"] },
    { "id": 8, "tasks": ["4.4", "4.5", "6.4", "6.5", "6.6", "7.5"] },
    { "id": 9, "tasks": ["4.6", "4.7"] },
    { "id": 10, "tasks": ["4.8", "4.9", "4.10", "4.11"] },
    { "id": 11, "tasks": ["9.1", "9.2", "10.1"] },
    { "id": 12, "tasks": ["10.2", "10.3"] },
    { "id": 13, "tasks": ["11.1", "11.2"] },
    { "id": 14, "tasks": ["11.3"] },
    { "id": 15, "tasks": ["12.1", "12.2", "12.3"] },
    { "id": 16, "tasks": ["12.4"] }
  ]
}
```
