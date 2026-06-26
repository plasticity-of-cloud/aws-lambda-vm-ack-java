# IAM & Security

## Overview

The KubeMicroVM operator requires AWS IAM permissions to manage Lambda MicroVM resources. This document describes the least-privilege IAM design following the AWS Well-Architected Framework security pillar.

## IAM Roles

### 1. Operator Role (Pod Identity / IRSA)

The primary role assumed by the operator pod. Deployed via `iam/kube-microvm-operator-role.yaml`.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "MicroVMLifecycle",
      "Effect": "Allow",
      "Action": [
        "lambda-microvms:RunMicroVm",
        "lambda-microvms:DescribeMicroVm",
        "lambda-microvms:SuspendMicroVm",
        "lambda-microvms:ResumeMicroVm",
        "lambda-microvms:TerminateMicroVm",
        "lambda-microvms:ListMicroVms",
        "lambda-microvms:CreateMicroVmAuthToken"
      ],
      "Resource": "arn:aws:lambda:*:${AccountId}:microvm:*"
    },
    {
      "Sid": "MicroVMImageManagement",
      "Effect": "Allow",
      "Action": [
        "lambda-microvms:CreateMicroVmImage",
        "lambda-microvms:UpdateMicroVmImage",
        "lambda-microvms:DeleteMicroVmImage",
        "lambda-microvms:DescribeMicroVmImage",
        "lambda-microvms:ListMicroVmImages",
        "lambda-microvms:CreateMicroVmImageVersion",
        "lambda-microvms:ActivateMicroVmImageVersion",
        "lambda-microvms:DeactivateMicroVmImageVersion"
      ],
      "Resource": "arn:aws:lambda:*:${AccountId}:microvm-image:*"
    },
    {
      "Sid": "NetworkConnectorManagement",
      "Effect": "Allow",
      "Action": [
        "lambda-core:CreateNetworkConnector",
        "lambda-core:DeleteNetworkConnector",
        "lambda-core:DescribeNetworkConnector",
        "lambda-core:ListNetworkConnectors"
      ],
      "Resource": "arn:aws:lambda:*:${AccountId}:network-connector:*"
    },
    {
      "Sid": "NetworkValidation",
      "Effect": "Allow",
      "Action": [
        "ec2:DescribeVpcs",
        "ec2:DescribeSubnets",
        "ec2:DescribeSecurityGroups"
      ],
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "aws:RequestedRegion": "${Region}"
        }
      }
    },
    {
      "Sid": "S3ReadArtifacts",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:HeadObject"
      ],
      "Resource": "arn:aws:s3:::${ArtifactBucket}/*",
      "Condition": {
        "StringLike": {
          "s3:prefix": "images/*"
        }
      }
    },
    {
      "Sid": "CallerIdentity",
      "Effect": "Allow",
      "Action": "sts:GetCallerIdentity",
      "Resource": "*"
    }
  ]
}
```

### 2. Network Connector Operator Role

A separate role assumed by the Lambda service to create ENIs. NOT assumed by the operator pod.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "CreateENI",
      "Effect": "Allow",
      "Action": "ec2:CreateNetworkInterface",
      "Resource": [
        "arn:aws:ec2:*:*:network-interface/*",
        "arn:aws:ec2:*:*:subnet/${AllowedSubnets}",
        "arn:aws:ec2:*:*:security-group/${AllowedSGs}"
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
    },
    {
      "Sid": "DeleteENI",
      "Effect": "Allow",
      "Action": "ec2:DeleteNetworkInterface",
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

Trust policy for this role:
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Service": "network-connectors.lambda.amazonaws.com"
    },
    "Action": "sts:AssumeRole"
  }]
}
```

## Authentication Methods

### EKS Pod Identity (recommended)

- No annotation on ServiceAccount required
- Create `PodIdentityAssociation` via EKS API
- Automatically injects credentials into the pod
- Supports cross-account access
- Fine-grained: scoped to specific namespace + service account

```bash
aws eks create-pod-identity-association \
  --cluster-name my-cluster \
  --namespace kube-system \
  --service-account kube-microvm-operator \
  --role-arn arn:aws:iam::123456789012:role/kube-microvm-operator-my-cluster
```

### IRSA (legacy, still supported)

- Requires `eks.amazonaws.com/role-arn` annotation on ServiceAccount
- Uses OIDC federation for trust
- Helm value: `--set serviceAccount.irsaRoleArn=<arn>`

## Security Boundaries

| Boundary | Enforcement |
|----------|-------------|
| Namespace isolation | MicroVMs can only reference images/networks in their own namespace |
| Resource quotas | Webhook enforces per-namespace MicroVM count and memory limits |
| Network segmentation | Each namespace can have its own MicroVMNetwork (separate connector) |
| Least privilege | Operator role has no `iam:*`, no `ec2:Create*`, no wildcard actions |
| Credential rotation | Pod Identity handles automatic credential refresh |
| Audit trail | All AWS API calls logged in CloudTrail with pod identity context |

## CloudFormation Templates

| Template | Location | Purpose |
|----------|----------|---------|
| Operator role | `iam/kube-microvm-operator-role.yaml` | Primary operator IAM role |
| Network connector role | `iam/network-connector-role.yaml` | Lambda service role for ENI management |

## Permissions Boundary (optional)

For organizations requiring permissions boundaries:

```yaml
# In the CloudFormation template
PermissionsBoundary:
  Type: String
  Default: ''
  Description: ARN of permissions boundary to apply to the operator role
```

This ensures the operator role cannot exceed organizational guardrails even if the policy is misconfigured.
