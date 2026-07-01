# AWS Lambda MicroVMs SDK Client

## Status

`lambda-microvms` is not yet published to the public botocore/boto3 repository or any official
AWS SDK release. The service model is distributed only inside the AWS CLI snap package.

## Service Model Source

The `service-2.json` and `paginators-1.json` bundled in `operator-aws-client/src/main/resources/codegen-resources/`
were copied from the local AWS CLI snap installation:

```
/snap/aws-cli/2277/aws/dist/awscli/botocore/data/lambda-microvms/2025-09-09/
```

The canonical public source for all AWS service models is the botocore repository:
- https://github.com/boto/botocore/tree/develop/botocore/data

`lambda-microvms` is not yet present there. When it is published, update the bundled model
by copying the new `service-2.json` and `paginators-1.json` and rebuilding `operator-aws-client`.

## Client Generation

The Java client is generated at build time by `software.amazon.awssdk:codegen-maven-plugin`
(same version as the AWS SDK for Java v2 used in the project) reading the botocore model directly.

Generated output: `operator-aws-client/target/generated-sources/sdk/`  
Root package: `ai.codriverlabs.microvm.aws.lambdamicrovms`

## API Details

| Property | Value |
|----------|-------|
| API version | `2025-09-09` |
| Protocol | `rest-json` |
| Endpoint prefix | `lambda` |
| Signing name | `lambda` |
| Endpoint pattern | `https://lambda.{region}.amazonaws.com/2025-09-09/...` |
| Operations | 24 |

The service shares the Lambda service endpoint (`lambda.{region}.amazonaws.com`) with a
versioned path prefix (`/2025-09-09/`) distinguishing MicroVMs operations from the standard
Lambda API.

## Regional Availability

As of 2026-06-28, the service is available in:

| Region | Status |
|--------|--------|
| `us-east-1` | ✅ Available |
| `us-east-2` | ✅ Available |
| `us-west-2` | ✅ Available |
| `eu-west-1` | ✅ Available |
| `eu-central-1` | ❌ Not available (403) |
| `ap-southeast-1` | ❌ Not available (403) |
| `ap-south-1` | ❌ Not available (403) |

**Implication**: the operator and the EKS cluster it runs on must be deployed in a supported
region. Cross-region deployments (e.g. operator in `ap-south-1`, MicroVMs in `us-east-1`)
are unsupported because MicroVM network connectors must be co-located with the MicroVM.

## Updating the Model

When AWS publishes `lambda-microvms` to botocore:

```bash
# Copy updated model files
cp <botocore>/data/lambda-microvms/2025-09-09/service-2.json \
   operator-aws-client/src/main/resources/codegen-resources/
cp <botocore>/data/lambda-microvms/2025-09-09/paginators-1.json \
   operator-aws-client/src/main/resources/codegen-resources/

# Rebuild the generated client
./mvnw -pl operator-aws-client clean install -DskipTests
```

The generated Java client will be regenerated automatically on every build — no manual
code changes are needed unless the API version changes.

## AWS Lambda Core (Network Connectors)

The `lambda-core` API handles Network Connector management (VPC egress for MicroVMs).
It uses a separate service model from `lambda-microvms`.

| Property | Value |
|----------|-------|
| API version | `2026-04-30` |
| Protocol | `rest-json` |
| Endpoint prefix | `lambda` |
| Signing name | `lambda` |
| Source module | `operator-aws-client-core` |
| Generated package | `ai.codriverlabs.microvm.aws.lambdacore` |

### Service Model Source

The `service-2.json` and `paginators-1.json` for lambda-core were also obtained from the
AWS CLI snap package:

```
/snap/aws-cli/2277/aws/dist/awscli/botocore/data/lambda-core/2026-04-30/
```

### ACK Controller

AWS Controllers for Kubernetes (ACK) has an official controller for Lambda Core:

- **Repository**: https://github.com/aws-controllers-k8s/lambdacore-controller
- **Status**: Public (same timeline as Lambda MicroVMs launch)
- **Resources managed**: Network Connectors (same API our `MicroVMNetworkReconciler` uses)

The ACK `lambdacore-controller` provides a Go-based alternative for managing Network Connectors
as Kubernetes custom resources. Our operator uses the same underlying API but via the Java SDK
codegen approach, integrated with our MicroVM lifecycle (e.g. blocking network deletion while
MicroVMs are attached).

### Relationship to KubeMicroVM

| Concern | KubeMicroVM Operator | ACK lambdacore-controller |
|---------|---------------------|---------------------------|
| Language | Java (Quarkus + JOSDK) | Go (ACK runtime) |
| Network Connectors | ✅ Integrated with MicroVM lifecycle | ✅ Standalone CRUD |
| MicroVM management | ✅ Full lifecycle | ❌ Not in scope |
| Image management | ✅ Full lifecycle | ❌ Not in scope |
| Deletion protection | ✅ Blocks if VMs attached | ❌ No cross-resource awareness |
| Auth token injection | ✅ Pod webhook + CLI | ❌ Not in scope |

For users who only need Network Connector management without the MicroVM operator, the ACK
controller is a lighter-weight alternative.
