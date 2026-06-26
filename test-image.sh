#!/usr/bin/env bash
# test-image.sh — end-to-end test of kubectl microvm image create / list / describe / delete
#
# Prerequisites: run ./setup-test-env.sh first
#
# Usage:
#   ./test-image.sh             # run full test cycle
#   ./test-image.sh --no-delete # keep the MicroVMImage CR after test
#
set -euo pipefail

NO_DELETE=false
[[ "${1:-}" == "--no-delete" ]] && NO_DELETE=true

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.test-env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Error: .test-env not found. Run ./setup-test-env.sh first." >&2
  exit 1
fi
source "${ENV_FILE}"

export PATH="${HOME}/.local/bin:${PATH}"

IMAGE_NAME="hello-node-$(date +%s)"
NAMESPACE="default"

echo "==> MicroVMImage end-to-end test"
echo "    image name : ${IMAGE_NAME}"
echo "    source     : s3://${BUCKET}/${S3_KEY}"
echo "    base image : ${BASE_IMAGE_ARN}"
echo ""

echo "--- create"
kubectl microvm image create \
  --name "${IMAGE_NAME}" \
  --s3-bucket "${BUCKET}" \
  --s3-key "${S3_KEY}" \
  --base-image "${BASE_IMAGE_ARN}" \
  --namespace "${NAMESPACE}" \
  --wait

echo ""
echo "--- list"
kubectl microvm image list --namespace "${NAMESPACE}"

echo ""
echo "--- describe"
kubectl microvm image describe --name "${IMAGE_NAME}" --namespace "${NAMESPACE}"

if ! $NO_DELETE; then
  echo ""
  echo "--- delete"
  kubectl microvm image delete --name "${IMAGE_NAME}" --namespace "${NAMESPACE}"
  echo "    Deleted ${IMAGE_NAME}"
fi

echo ""
echo "==> Test complete"
