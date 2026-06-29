#!/usr/bin/env bash
# deploy-local.sh — build (optional) + deploy KubeMicroVM operator via Helm
#
# Usage:
#   ./deploy-local.sh                        # build JVM + deploy to current kubectl context
#   ./deploy-local.sh --skip-build           # deploy only (reuse existing image)
#   ./deploy-local.sh --native               # native build + deploy
#   ./deploy-local.sh --namespace <ns>       # deploy to specific namespace (default: kube-microvm)
#   ./deploy-local.sh --region <region>      # AWS region (default: from aws config)
#   ./deploy-local.sh --role-arn <arn>       # IRSA role ARN (skips Pod Identity setup)
#   ./deploy-local.sh --skip-pod-identity    # skip Pod Identity role + association creation
#   ./deploy-local.sh --eks-d-xpress         # use EKS-D-Xpress CLI for Pod Identity association
#   ./deploy-local.sh --dry-run              # helm install --dry-run
#
set -euo pipefail

SKIP_BUILD=false
NATIVE=false
NAMESPACE="kube-microvm"
REGION=""
ROLE_ARN=""
DRY_RUN=false
SKIP_POD_IDENTITY=false
EKS_DX=false
EKS_DX_CLI=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --help)
      sed -n '2,10p' "$0" | sed 's/^# //'
      exit 0 ;;
    --skip-build)        SKIP_BUILD=true ;;
    --native)            NATIVE=true ;;
    --namespace)         NAMESPACE="$2"; shift ;;
    --namespace=*)       NAMESPACE="${1#--namespace=}" ;;
    --region)            REGION="$2"; shift ;;
    --region=*)          REGION="${1#--region=}" ;;
    --role-arn)          ROLE_ARN="$2"; shift ;;
    --role-arn=*)        ROLE_ARN="${1#--role-arn=}" ;;
    --skip-pod-identity) SKIP_POD_IDENTITY=true ;;
    --eks-d-xpress)      EKS_DX=true ;;
    --dry-run)           DRY_RUN=true ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
[[ -n "$REGION" ]] || REGION=$(aws configure get region || echo "ap-south-1")
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
IMAGE_TAG=$(git describe --tags 2>/dev/null | sed 's/^v//;s/-[0-9]*-g[0-9a-f]*$//' || echo "dev")
IMAGE_REPO="${ECR_REGISTRY}/plasticity-of-cloud/kube-microvm-operator"

echo "==> KubeMicroVM deploy"
echo "    context  : $(kubectl config current-context)"
echo "    namespace: ${NAMESPACE}"
echo "    image    : ${IMAGE_REPO}:${IMAGE_TAG}"
echo "    region   : ${REGION}"

# 1. Build + push
if ! $SKIP_BUILD; then
  NATIVE_FLAG=""
  $NATIVE && NATIVE_FLAG="--native"
  ./build-local.sh --push ${NATIVE_FLAG}
fi

# 2. Ensure namespace
kubectl get namespace "${NAMESPACE}" &>/dev/null \
  || kubectl create namespace "${NAMESPACE}"

# 3. Pod Identity role + association (unless IRSA or explicitly skipped)
CLUSTER_NAME=$(kubectl config current-context | sed 's|.*cluster/||')
SA_NAME="kube-microvm-operator"
POD_IDENTITY_ROLE_NAME="kube-microvm-operator"

if [[ -z "$ROLE_ARN" ]] && ! $SKIP_POD_IDENTITY && ! $DRY_RUN; then
  echo "--- Pod Identity"

  # Resolve eks-dx CLI if requested
  if $EKS_DX; then
    EKS_DX_DIR="/home/ubuntu/projects/pl-cloud/eks-dx/eks-d-xpress-control-plane"
    EKS_DX_CLI="${EKS_DX_DIR}/eks-dx-cli.sh"
    [[ -x "$EKS_DX_CLI" ]] || { echo "eks-dx CLI not found at ${EKS_DX_CLI}" >&2; exit 1; }
    echo "    Using EKS-D-Xpress CLI for Pod Identity"
  fi

  # Create role if it doesn't exist
  if ! aws iam get-role --role-name "${POD_IDENTITY_ROLE_NAME}" &>/dev/null; then
    echo "    Creating IAM role ${POD_IDENTITY_ROLE_NAME}"
    aws iam create-role \
      --role-name "${POD_IDENTITY_ROLE_NAME}" \
      --assume-role-policy-document '{
        "Version":"2012-10-17",
        "Statement":[{"Effect":"Allow","Principal":{"Service":"pods.eks.amazonaws.com"},
          "Action":["sts:AssumeRole","sts:TagSession"]}]}' \
      --description "KubeMicroVM operator Pod Identity role" \
      --query 'Role.Arn' --output text
    aws iam put-role-policy \
      --role-name "${POD_IDENTITY_ROLE_NAME}" \
      --policy-name KubeMicroVMOperatorPolicy \
      --policy-document '{
        "Version":"2012-10-17",
        "Statement":[{"Effect":"Allow",
          "Action":["lambda-microvms:RunMicrovm","lambda-microvms:GetMicrovm",
            "lambda-microvms:ListMicrovms","lambda-microvms:SuspendMicrovm",
            "lambda-microvms:ResumeMicrovm","lambda-microvms:TerminateMicrovm",
            "lambda-microvms:CreateMicrovmImage","lambda-microvms:GetMicrovmImage",
            "lambda-microvms:UpdateMicrovmImage","lambda-microvms:DeleteMicrovmImage",
            "lambda-microvms:ListMicrovmImages","lambda-microvms:GetMicrovmImageVersion",
            "lambda-microvms:ListMicrovmImageVersions","lambda-microvms:UpdateMicrovmImageVersion",
            "lambda-microvms:ListManagedMicrovmImages","lambda-microvms:CreateMicrovmAuthToken",
            "lambda-microvms:TagResource","lambda-microvms:UntagResource","lambda-microvms:ListTags"],
          "Resource":"*"}]}'
  else
    echo "    IAM role ${POD_IDENTITY_ROLE_NAME} already exists"
  fi

  ROLE_ARN_PI=$(aws iam get-role --role-name "${POD_IDENTITY_ROLE_NAME}" --query 'Role.Arn' --output text)

  # Tag role for EKS-D-Xpress managed trust policy
  if $EKS_DX; then
    echo "    Tagging role with eks-dx-managed=true"
    aws iam tag-role \
      --role-name "${POD_IDENTITY_ROLE_NAME}" \
      --tags Key=eks-dx-managed,Value=true
  fi

  # Create association if it doesn't exist
  if $EKS_DX; then
    # Check via eks-dx CLI
    EXISTING=$(cd "${EKS_DX_DIR}" && ./eks-dx-cli.sh list-associations \
      --cluster-name "${CLUSTER_NAME}" 2>/dev/null \
      | grep "${SA_NAME}" | grep "${NAMESPACE}" | head -1 || true)
    if [[ -z "$EXISTING" ]]; then
      echo "    Creating Pod Identity association (via eks-dx)"
      cd "${EKS_DX_DIR}" && ./eks-dx-cli.sh create-association \
        --cluster-name "${CLUSTER_NAME}" \
        --namespace "${NAMESPACE}" \
        --service-account "${SA_NAME}" \
        --role-arn "${ROLE_ARN_PI}"
      cd - > /dev/null
    else
      echo "    Pod Identity association already exists (eks-dx)"
    fi
  else
    EXISTING=$(aws eks list-pod-identity-associations \
      --cluster-name "${CLUSTER_NAME}" \
      --namespace "${NAMESPACE}" \
      --region "${REGION}" \
      --query "associations[?serviceAccount=='${SA_NAME}'].associationId" \
      --output text 2>/dev/null)
    if [[ -z "$EXISTING" ]]; then
      echo "    Creating Pod Identity association"
      aws eks create-pod-identity-association \
        --cluster-name "${CLUSTER_NAME}" \
        --namespace "${NAMESPACE}" \
        --service-account "${SA_NAME}" \
        --role-arn "${ROLE_ARN_PI}" \
        --region "${REGION}" \
        --query 'association.associationId' --output text
    else
      echo "    Pod Identity association already exists: ${EXISTING}"
    fi
  fi
fi

# 4. Helm install / upgrade
DRY_RUN_FLAG=""
$DRY_RUN && DRY_RUN_FLAG="--dry-run"

HELM_SET_ROLE=""
[[ -n "$ROLE_ARN" ]] && HELM_SET_ROLE="--set serviceAccount.irsaRoleArn=${ROLE_ARN}"

helm upgrade --install kube-microvm-operator \
  charts/kube-microvm-operator \
  --namespace "${NAMESPACE}" \
  --set image.repository="${IMAGE_REPO}" \
  --set image.tag="${IMAGE_TAG}" \
  --set image.pullPolicy=Always \
  --set aws.region="${REGION}" \
  ${HELM_SET_ROLE} \
  --wait --timeout 120s \
  ${DRY_RUN_FLAG}

echo ""
echo "==> Deploy complete"
echo "    kubectl get pods -n ${NAMESPACE}"
kubectl get pods -n "${NAMESPACE}" 2>/dev/null || true
