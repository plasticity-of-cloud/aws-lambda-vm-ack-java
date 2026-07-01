# Build & Test Requirements

## Pre-Push Verification

All builds, unit tests, and integration tests **must pass locally** before pushing code changes.

```bash
# Full verification (required before every push)
cd /home/ubuntu/projects/pl-cloud/KubeMicroVM
./mvnw -B install -DskipTests --no-transfer-progress -q && \
./mvnw -B -pl operator-tests verify --no-transfer-progress
```

If tests fail, fix the issue before pushing. Do not push with `--skip-tests` unless:
1. The change is documentation-only (`.md`, `.kiro/`)
2. The change is CI/workflow-only (`.github/`)

## What Gets Verified

| Step | Command | What it checks |
|------|---------|----------------|
| Compile | `./mvnw install -DskipTests` | All modules compile, SDK codegen works |
| Integration tests | `./mvnw -pl operator-tests verify` | All 47 integration tests pass (mocked AWS, reconciler logic, webhooks, token endpoint) |

## Common Failures

- **SDK client changes** (DefaultMicroVMClient, MicroVMImageClient, MicroVMNetworkClient): these are injected via CDI — constructor signature changes break tests
- **application.properties changes**: `%test` profile must disable TLS, endpoints, etc.
- **CRD model changes**: integration tests use real CR instances — field renames break them

## Quick Commands

```bash
# Just compile (fast check)
./mvnw -B install -DskipTests -q

# Run only integration tests
./mvnw -B -pl operator-tests verify

# Run a single test class
./mvnw -B -pl operator-tests verify -Dit.test=MicroVMImageReconcilerIT

# Full build + push (the correct workflow)
./mvnw -B install -DskipTests -q && \
./mvnw -B -pl operator-tests verify && \
git push
```
