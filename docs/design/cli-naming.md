# CLI Binary Naming Strategy

## Problem

The `kubectl-microvm` binary name causes shell autocomplete interference.
When kubectl's bash/zsh completion scans `$PATH` for `kubectl-*` binaries,
it registers `microvm` as a kubectl subcommand — but without proper completion
metadata, causing tab completion to stall or not insert a space after `kubectl`.

## Solution: Ship Two Binaries

Install **both** `microvm` (primary) and `kubectl-microvm` (symlink):

```
$HOME/bin/microvm            ← standalone binary, full shell completion
$HOME/bin/kubectl-microvm    ← symlink → microvm, enables `kubectl microvm`
```

### Primary interface: `microvm`

- Own completion script: `source <(microvm completion bash)`
- No interference with kubectl completion
- Shorter to type: `microvm token`, `microvm image create`
- Works in any shell without kubectl installed

### Secondary interface: `kubectl microvm`

- Works via kubectl plugin discovery (kubectl scans PATH for `kubectl-*`)
- No change to existing users who rely on `kubectl microvm`
- Symlink means same binary — one codebase, one binary, two entry points

## Implementation

### 1. Rename Picocli `@Command` name

The top-level command name in `MicroVMCommand.java` should be `microvm`:

```java
@Command(
    name = "microvm",           // ← was "kubectl-microvm"
    description = "CLI for managing AWS Lambda MicroVMs"
)
```

This fixes the `Usage: kubectl-microvm` shown in `--help` output.

### 2. Build output: single binary named `microvm`

In the native build profile, output the binary as `microvm`:

```xml
<quarkus.package.output-name>microvm</quarkus.package.output-name>
```

### 3. Install script creates symlink

`install_kube_microvm.sh` installs both:

```bash
INSTALL_DIR="$HOME/bin"
cp microvm-linux-arm64 "$INSTALL_DIR/microvm"
chmod +x "$INSTALL_DIR/microvm"
ln -sf "$INSTALL_DIR/microvm" "$INSTALL_DIR/kubectl-microvm"

# Source completion (add to ~/.bashrc)
echo 'source <(microvm completion bash)' >> ~/.bashrc
```

### 4. Shell completion

Picocli generates completion via `generate-completion` subcommand.
Add it to the CLI:

```bash
# Bash
source <(microvm completion bash)

# Zsh
source <(microvm completion zsh)
```

The `microvm completion bash` output registers `microvm` as a top-level
command with all subcommands — completely independent of kubectl completion.

## Behavior After Fix

```bash
# Primary — clean completion, no interference
microvm <TAB>
microvm token --<TAB>
microvm image <TAB>

# kubectl plugin — still works
kubectl microvm token --name my-vm

# help shows correct name
microvm --help
# Usage: microvm [-hV] [COMMAND]
```

## Files to Change

| File | Change |
|------|--------|
| `operator-cli/.../MicroVMCommand.java` | `name = "microvm"` |
| `operator-cli/pom.xml` | `quarkus.package.output-name=microvm` |
| `install_kube_microvm.sh` | install `microvm` + symlink `kubectl-microvm` |
| `install_kube_microvm.sh` | `source <(microvm completion bash)` in `.bashrc` |
| `native-build.yml` | rename artifact from `kubectl-microvm-*` to `microvm-*` |
| `README.md` | update install instructions |

## Feature Branch

`feature/cli-rename-microvm`
