#!/usr/bin/env bash
# install-kubectl-plugin.sh — build (if needed) and install kubectl-microvm as a kubectl plugin
#
# Usage:
#   ./install-kubectl-plugin.sh              # JVM fat-jar wrapper (fast build)
#   ./install-kubectl-plugin.sh --native     # GraalVM native binary (slow first build)
#   ./install-kubectl-plugin.sh --skip-build # install from existing build output
#   ./install-kubectl-plugin.sh --prefix <dir>  # install to custom prefix (default: ~/.local/bin)
#   ./install-kubectl-plugin.sh --uninstall  # remove plugin
#
set -euo pipefail

NATIVE=false
SKIP_BUILD=false
PREFIX="${HOME}/.local/bin"
UNINSTALL=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --help)
      sed -n '2,8p' "$0" | sed 's/^# //'
      exit 0 ;;
    --native)       NATIVE=true ;;
    --skip-build)   SKIP_BUILD=true ;;
    --prefix)       PREFIX="$2"; shift ;;
    --prefix=*)     PREFIX="${1#--prefix=}" ;;
    --uninstall)    UNINSTALL=true ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

PLUGIN_NAME="kubectl-microvm"
INSTALL_PATH="${PREFIX}/${PLUGIN_NAME}"

# Uninstall
if $UNINSTALL; then
  rm -f "${INSTALL_PATH}"
  echo "==> Removed ${INSTALL_PATH}"
  exit 0
fi

# Build
if ! $SKIP_BUILD; then
  NATIVE_FLAG=""
  $NATIVE && NATIVE_FLAG="--native"
  ./build-local.sh --only cli --skip-tests ${NATIVE_FLAG}
fi

mkdir -p "${PREFIX}"

# Locate build output
NATIVE_BIN="operator-cli/target/operator-cli-1.0.0-SNAPSHOT-runner"
QUARKUS_APP_DIR="operator-cli/target/quarkus-app"

if $NATIVE && [[ -f "$NATIVE_BIN" ]]; then
  # Install native binary directly
  echo "==> Installing native binary → ${INSTALL_PATH}"
  cp "${NATIVE_BIN}" "${INSTALL_PATH}"
  chmod +x "${INSTALL_PATH}"

elif [[ -d "$QUARKUS_APP_DIR" ]]; then
  # Install JVM wrapper script that launches the Quarkus fast-jar
  APP_DIR_ABS="$(cd "${QUARKUS_APP_DIR}" && pwd)"
  echo "==> Installing JVM wrapper → ${INSTALL_PATH}"
  cat > "${INSTALL_PATH}" <<WRAPPER
#!/usr/bin/env bash
exec java -jar "${APP_DIR_ABS}/quarkus-run.jar" "\$@"
WRAPPER
  chmod +x "${INSTALL_PATH}"

else
  echo "Error: no build output found. Run without --skip-build first." >&2
  exit 1
fi

# Verify PATH
if ! echo ":${PATH}:" | grep -q ":${PREFIX}:"; then
  echo ""
  echo "  ⚠  ${PREFIX} is not in your PATH."
  echo "  Add to your shell profile:"
  echo "    export PATH=\"${PREFIX}:\$PATH\""
fi

echo ""
echo "==> kubectl-microvm installed at ${INSTALL_PATH}"
echo "    Verify:  kubectl microvm --help"
"${INSTALL_PATH}" --help 2>/dev/null | head -6 || true
