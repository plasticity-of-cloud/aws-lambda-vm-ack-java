#!/usr/bin/env bash
# deploy-local.sh — build (optional) + deploy KubeMicroVM operator via Helm
#
# Usage:
#   ./deploy-local.sh                        # build JVM + deploy to current kubectl context
#   ./deploy-local.sh --skip-build           # deploy only (reuse existing image)
#   ./deploy-local.sh --native               # native build + deploy
#   ./deploy-local.sh --namespace <ns>       # deploy to specific namespace (default: kube-microvm)
#   ./deploy-local.sh --region <region>      # AWS region (default: from aws config)
#   ./deploy-local.sh --role-arn <arn>       # IRSA role ARN (optional)
#   ./deploy-local.sh --dry-run              # helm install --dry-run
#
set -euo pipefail

SKIP_BUILD=false
NATIVE=false
NAMESPACE="kube-microvm"
REGION=""
ROLE_ARN=""
DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --help)
      sed -n '2,9p' "$0" | sed 's/^# //'
      exit 0 ;;
    --skip-build)   SKIP_BUILD=true ;;
    --native)       NATIVE=true ;;
    --namespace)    NAMESPACE="$2"; shift ;;
    --namespace=*)  NAMESPACE="${1#--namespace=}" ;;
    --region)       REGION="$2"; shift ;;
    --region=*)     REGION="${1#--region=}" ;;
    --role-arn)     ROLE_ARN="$2"; shift ;;
    --role-arn=*)   ROLE_ARN="${1#--role-arn=}" ;;
    --dry-run)      DRY_RUN=true ;;
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

# 3. Helm install / upgrade
DRY_RUN_FLAG=""
$DRY_RUN && DRY_RUN_FLAG="--dry-run"

HELM_SET_ROLE=""
[[ -n "$ROLE_ARN" ]] && HELM_SET_ROLE="--set serviceAccount.irsaRoleArn=${ROLE_ARN}"

helm upgrade --install kube-microvm-operator \
  charts/lambda-vm-ack-operator \
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
