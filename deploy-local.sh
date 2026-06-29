#!/usr/bin/env bash
# deploy-local.sh — deploy KubeMicroVM operator to an EKS cluster
#
# Usage:
#   ./deploy-local.sh                              # build + deploy from local chart
#   ./deploy-local.sh --skip-build                 # helm upgrade only (reuse existing chart)
#   ./deploy-local.sh --from-registry              # deploy from GHCR OCI (no local build)
#   ./deploy-local.sh --region us-east-1           # target AWS region
#   ./deploy-local.sh --cluster my-cluster         # EKS cluster name
#   ./deploy-local.sh --profile my-aws-profile     # AWS CLI profile
#   ./deploy-local.sh --dry-run                    # helm upgrade --dry-run
#   ./deploy-local.sh --set key=val                # extra helm values (repeatable)
#
set -euo pipefail

SKIP_BUILD=false
FROM_REGISTRY=false
DRY_RUN=false
REGION="${AWS_REGION:-us-east-1}"
CLUSTER=""
AWS_PROFILE=""
EXTRA_SET_ARGS=""
NAMESPACE="kube-microvm"
RELEASE="kube-microvm-operator"

while [[ $# -gt 0 ]]; do
  case $1 in
    --help)
      echo "Usage: ./deploy-local.sh [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --skip-build          Skip Maven build; use existing chart tarball"
      echo "  --from-registry       Deploy from GHCR OCI (ignores local build)"
      echo "  --region <region>     AWS region (default: \$AWS_REGION or us-east-1)"
      echo "  --cluster <name>      EKS cluster name (updates kubeconfig)"
      echo "  --profile <name>      AWS CLI profile"
      echo "  --namespace <ns>      Kubernetes namespace (default: kube-microvm)"
      echo "  --dry-run             Helm dry-run (no actual deploy)"
      echo "  --set key=val         Extra helm --set values (repeatable)"
      echo "  --help                Show this help"
      echo ""
      echo "Examples:"
      echo "  ./deploy-local.sh --region us-east-1 --cluster my-cluster"
      echo "  ./deploy-local.sh --skip-build --dry-run"
      echo "  ./deploy-local.sh --from-registry --region us-east-2"
      echo "  ./deploy-local.sh --set 'app.envs.microvm\\.aws\\.region=us-west-2'"
      exit 0
      ;;
    --skip-build)    SKIP_BUILD=true ;;
    --from-registry) FROM_REGISTRY=true; SKIP_BUILD=true ;;
    --dry-run)       DRY_RUN=true ;;
    --region)        REGION="$2"; shift ;;
    --cluster)       CLUSTER="$2"; shift ;;
    --profile)       AWS_PROFILE="$2"; shift ;;
    --namespace)     NAMESPACE="$2"; shift ;;
    --set)           EXTRA_SET_ARGS="${EXTRA_SET_ARGS} --set $2"; shift ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

PROFILE_FLAG=""; [[ -n "$AWS_PROFILE" ]] && PROFILE_FLAG="--profile $AWS_PROFILE"
IMAGE_TAG=$(git describe --tags 2>/dev/null | sed 's/^v//;s/-[0-9]*-g[0-9a-f]*$//' || echo "dev")
DRY_RUN_FLAG=""; $DRY_RUN && DRY_RUN_FLAG="--dry-run"

# Update kubeconfig if cluster specified
if [[ -n "$CLUSTER" ]]; then
  echo "==> Updating kubeconfig for cluster ${CLUSTER} in ${REGION}"
  aws eks update-kubeconfig --region "$REGION" --name "$CLUSTER" $PROFILE_FLAG
fi

# Build if needed
if ! $SKIP_BUILD && ! $FROM_REGISTRY; then
  echo "==> Building operator (tag: ${IMAGE_TAG})"
  ./build-local.sh --helm --skip-tests
fi

# Resolve chart source
if $FROM_REGISTRY; then
  CHART="oci://ghcr.io/plasticity-of-cloud/helm/kube-microvm-operator"
  echo "==> Deploying from GHCR OCI registry"
else
  CHART=$(ls operator-controller/target/helm/kubernetes/kube-microvm-operator-*.tar.gz 2>/dev/null | head -1)
  if [[ -z "$CHART" ]]; then
    echo "ERROR: No chart tarball found. Run ./build-local.sh --helm first." >&2
    exit 1
  fi
  echo "==> Deploying from local chart: ${CHART}"
fi

# Ensure namespace exists
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# Helm upgrade --install
echo "==> helm upgrade --install ${RELEASE} (namespace: ${NAMESPACE}, dry-run: ${DRY_RUN})"
helm upgrade --install "$RELEASE" "$CHART" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  --set "app.envs.AWS_REGION=${REGION}" \
  $EXTRA_SET_ARGS \
  $DRY_RUN_FLAG \
  --wait \
  --timeout 5m

if ! $DRY_RUN; then
  echo ""
  echo "==> Deploy complete"
  echo "    Operator pod: $(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=kube-microvm-operator -o name 2>/dev/null | head -1)"
  echo ""
  echo "    To install kubectl plugin:"
  echo "    ./install-kubectl-plugin.sh"
fi
