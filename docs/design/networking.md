# Networking Design

## AWS Lambda MicroVM Networking Model

Lambda MicroVMs run in **AWS-managed infrastructure** (Fargate-style). They do NOT run inside customer VPCs. The networking model is:

```
┌──────────────────────────────────────────────────────┐
│                AWS-Managed Infrastructure             │
│  ┌─────────────┐                                     │
│  │   MicroVM   │ ← Firecracker VM with snapshot      │
│  │  (your app) │                                     │
│  └──────┬──────┘                                     │
│         │                                            │
│  ┌──────┴──────┐                                     │
│  │   Ingress   │ ← Service-managed HTTPS endpoint    │
│  │  Endpoint   │   (unique per MicroVM)              │
│  └──────┬──────┘                                     │
└─────────┼────────────────────────────────────────────┘
          │ HTTPS (TLS-terminated, JWE auth)
          ↕
    ┌─────────────┐
    │   Clients   │
    └─────────────┘

          Egress (outbound from MicroVM):
          ├── Default: public internet
          └── VPC: via Network Connector (ENI in customer subnet)
```

## Inbound Connectivity

- Each MicroVM gets a **unique HTTPS endpoint** assigned by AWS at `run-microvm` time
- Supports HTTP/1.1, HTTP/2, WebSockets, gRPC, SSE
- Port routing via `X-aws-proxy-port` header (default: 8080)
- Authentication via JWE tokens (`X-aws-proxy-auth` header)
- **No customer infrastructure needed** for inbound — no load balancers, no ingress controllers

## Outbound Connectivity

- **Default**: public internet egress (no config needed)
- **VPC access**: Create a Network Connector that provisions ENIs in customer subnets

## MicroVMNetwork CRD → Network Connector Mapping

The `MicroVMNetwork` CRD is a **reconciled resource**. The operator creates and manages an AWS Lambda Network Connector based on the CR spec.

> **Important**: Network Connectors are managed via the `lambda-core` AWS API (service ID `Lambda Core`, endpoint prefix `lambda`), **not** the `lambda-microvms` API. The operator requires a second generated SDK client (`operator-aws-client-core`) built from the `lambda-core` botocore service model.

```
MicroVMNetwork CR (Kubernetes)
        ↓ reconcile
LambdaCoreClient.createNetworkConnector()
        ↓ provisions
ENIs in customer VPC subnets
        ↓ routes
Outbound traffic from MicroVM → customer VPC → private resources
```

### Reconciliation Flow

```
1. User creates MicroVMNetwork CR
2. Operator calls LambdaCoreClient.createNetworkConnector()
   - Passes SubnetIds, SecurityGroupIds, NetworkProtocol, OperatorRoleArn
   - Note: VpcId is NOT a parameter — Lambda Core derives VPC from subnets
3. AWS provisions ENIs in specified subnets (PENDING → ACTIVE)
4. Operator updates status.connectorArn, status.connectorState
5. MicroVMs referencing this network can now use the connector ARN at run-microvm time
```

### IAM Requirements for Network Connectors

The operator's IAM role does NOT create ENIs directly. Instead, a separate **operator role** (specified in `spec.operatorRoleArn`) is assumed by the Lambda service to create ENIs:

```json
{
  "Statement": [
    {
      "Sid": "CreateENI",
      "Effect": "Allow",
      "Action": "ec2:CreateNetworkInterface",
      "Resource": [
        "arn:aws:ec2:*:*:network-interface/*",
        "arn:aws:ec2:*:*:subnet/*",
        "arn:aws:ec2:*:*:security-group/*"
      ]
    },
    {
      "Sid": "TagENI",
      "Effect": "Allow",
      "Action": "ec2:CreateTags",
      "Resource": "arn:aws:ec2:*:*:network-interface/*",
      "Condition": {
        "StringEquals": {
          "ec2:ManagedResourceOperator": "network-connectors.lambda.amazonaws.com"
        }
      }
    }
  ]
}
```

This role is created by the customer (via our CloudFormation template in `iam/`) and referenced in the `MicroVMNetwork` CR.

### Validation

The webhook validates `MicroVMNetwork` at creation:
- SubnetIds must be non-empty
- SecurityGroupIds must be non-empty
- OperatorRoleArn must be a valid IAM role ARN format
- The operator verifies subnets/SGs exist via `ec2:Describe*` before calling CreateNetworkConnector

### Network Connector Lifecycle

| MicroVMNetwork CR state | AWS Connector state | Operator action |
|------------------------|--------------------|--------------------|
| Created | — | Call CreateNetworkConnector |
| Synced | PENDING | Poll until ACTIVE or FAILED |
| Ready | ACTIVE | No action (set condition Ready=True) |
| Synced | FAILED | Set condition Ready=False, emit warning event |
| Deleted | ACTIVE → DELETING | Call DeleteNetworkConnector (after verifying no MicroVMs reference it) |

### Finalizer

`MicroVMNetwork` has a finalizer `lambda.aws.amazon.com/network-connector-finalizer` to ensure the AWS Network Connector is deleted before the CR is removed.

## MicroVM ↔ Network Association

When a `MicroVM` is created with `spec.networkRef`, the operator:

1. Looks up the referenced `MicroVMNetwork` CR
2. Checks `status.connectorState == Active`
3. Passes `status.connectorArn` as the `--egress-network-connectors` parameter to `run-microvm`

If the connector is not Active, the MicroVM stays in `Pending` state with condition `NetworkReady=False`.
