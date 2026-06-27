#!/usr/bin/env bash
# build-local.sh — build KubeMicroVM operator + CLI
#
# Usage:
#   ./build-local.sh                  # JVM build, no image push
#   ./build-local.sh --native         # GraalVM native CLI + operator image
#   ./build-local.sh --skip-tests     # skip unit tests
#   ./build-local.sh --push           # build & push container images to ECR
#   ./build-local.sh --registry <url> # override registry (default: auto ECR)
#   ./build-local.sh --only cli       # build only: operator | cli | all (default)
#
set -euo pipefail

NATIVE=false
SKIP_TESTS=false
PUSH=false
REGISTRY=""
ONLY="all"

while [[ $# -gt 0 ]]; do
  case $1 in
    --help)
      sed -n '2,9p' "$0" | sed 's/^# //'
      exit 0 ;;
    --native)       NATIVE=true ;;
    --skip-tests)   SKIP_TESTS=true ;;
    --push)         PUSH=true ;;
    --only)         ONLY="$2"; shift ;;
    --only=*)       ONLY="${1#--only=}" ;;
    --registry)     REGISTRY="$2"; shift ;;
    --registry=*)   REGISTRY="${1#--registry=}" ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

SKIP_FLAG=""
$SKIP_TESTS && SKIP_FLAG="-DskipTests"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=$(aws configure get region || echo "ap-south-1")
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
[[ -n "$REGISTRY" ]] || REGISTRY="$ECR_REGISTRY"

IMAGE_TAG=$(git describe --tags 2>/dev/null | sed 's/^v//;s/-[0-9]*-g[0-9a-f]*$//' || echo "dev")
echo "==> KubeMicroVM build  native=${NATIVE}  skipTests=${SKIP_TESTS}  only=${ONLY}  push=${PUSH}  tag=${IMAGE_TAG}"

# ECR login when pushing
if $PUSH; then
  echo "==> ECR login (${REGION})"
  aws ecr get-login-password --region "${REGION}" \
    | docker login --username AWS --password-stdin "${REGISTRY}"
  echo "==> ECR Public login (for base images)"
  aws ecr-public get-login-password --region us-east-1 \
    | docker login --username AWS --password-stdin public.ecr.aws
fi

ensure_ecr_repo() {
  local repo="$1"
  aws ecr describe-repositories --repository-names "$repo" --region "${REGION}" &>/dev/null \
    || aws ecr create-repository --repository-name "$repo" --region "${REGION}" \
         --image-scanning-configuration scanOnPush=true \
         --query 'repository.repositoryName' --output text
}

# 0. Parent + core (always needed)
echo "--- [0] parent + operator-core"
./mvnw -B -N install $SKIP_FLAG
./mvnw -B -pl operator-core install $SKIP_FLAG

# 1. Operator controller image
if [[ "$ONLY" == "all" || "$ONLY" == "operator" ]]; then
  echo "--- [1] operator-controller"

  IMAGE_FLAGS=""
  if $PUSH; then
    ensure_ecr_repo "plasticity-of-cloud/kube-microvm-operator"
    IMAGE_FLAGS="-Dquarkus.container-image.build=true \
      -Dquarkus.container-image.push=true \
      -Dquarkus.container-image.registry=${REGISTRY} \
      -Dquarkus.container-image.group=plasticity-of-cloud \
      -Dquarkus.container-image.name=kube-microvm-operator \
      -Dquarkus.container-image.tag=${IMAGE_TAG}"
  fi

  if $NATIVE; then
    ./mvnw -B -pl operator-controller -am clean package $SKIP_FLAG -Pnative $IMAGE_FLAGS
  else
    ./mvnw -B -pl operator-controller -am clean package $SKIP_FLAG $IMAGE_FLAGS
  fi
fi

# 2. Webhook image (bundled into controller in JVM mode; native = separate)
if [[ "$ONLY" == "all" ]]; then
  echo "--- [2] operator-webhook"
  ./mvnw -B -pl operator-webhook -am clean package $SKIP_FLAG
fi

# 3. CLI
if [[ "$ONLY" == "all" || "$ONLY" == "cli" ]]; then
  echo "--- [3] operator-cli"
  if $NATIVE; then
    ./mvnw -B -pl operator-cli -am clean package $SKIP_FLAG -Pnative \
      -Dquarkus.native.container-build=false
  else
    ./mvnw -B -pl operator-cli -am clean package $SKIP_FLAG
  fi

  CLI_BIN="operator-cli/target/operator-cli-1.0.0-SNAPSHOT-runner"
  if [[ -f "$CLI_BIN" ]]; then
    echo "==> CLI binary: ${CLI_BIN}"
  else
    echo "==> CLI jar: operator-cli/target/quarkus-app/"
  fi
fi

echo ""
echo "==> Build complete  (image tag: ${IMAGE_TAG})"
[[ "$ONLY" == "all" || "$ONLY" == "operator" ]] && \
  echo "    Operator image: ${REGISTRY}/plasticity-of-cloud/kube-microvm-operator:${IMAGE_TAG}"
