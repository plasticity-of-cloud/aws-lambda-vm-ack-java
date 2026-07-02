# Namespace Watching Design

## Overview

The KubeMicroVM operator watches only namespaces explicitly opted-in by a
cluster administrator. This reduces noise, limits blast radius, and prevents
stuck finalizers in unmanaged namespaces.

## Mechanism: Namespace Label Selector

The operator uses a JOSDK namespace label selector to filter which namespaces
it watches. Only namespaces with the label:

```
lambda.aws.amazon.com/manage-microvms=true
```

are watched by the reconcilers. Namespaces without this label are completely
ignored — the operator never processes CRs there, never places finalizers, and
never holds up deletions.

## Why Label, Not Annotation

- **Labels** are selectable in Kubernetes — they can drive watch filters natively
- **Annotations** are metadata only — they cannot be used as selectors
- Using a label means the watch filter and the permission model share one source
  of truth: the label presence = the namespace is managed

## Default Behavior

At install time, the Helm chart labels the `default` namespace automatically:

```bash
kubectl label namespace default lambda.aws.amazon.com/manage-microvms=true
```

All other namespaces are ignored unless explicitly labelled.

## Enabling a Namespace

Cluster admin opts a namespace in by adding the label:

```bash
kubectl label namespace my-team lambda.aws.amazon.com/manage-microvms=true
```

To remove management (operator stops watching, no new VMs can be created):

```bash
kubectl label namespace my-team lambda.aws.amazon.com/manage-microvms-
```

Note: Removing the label while VMs exist in that namespace will leave existing
VMs running on AWS but orphaned from the operator. Always terminate VMs before
removing the label.

## Implementation: JOSDK Namespace Selector

The reconcilers use `@ControllerConfiguration` with a namespace label selector:

```java
@ControllerConfiguration(
    labelSelector = @LabelSelector(
        matchLabels = "lambda.aws.amazon.com/manage-microvms=true"
    )
)
```

Or via `application.properties` per controller:

```properties
quarkus.operator-sdk.controllers.microvmreconciler.namespace-selector.match-labels[0]=lambda.aws.amazon.com/manage-microvms=true
```

The validating webhook also checks for the label (replaces the previous
annotation check) so the gate is consistent:

```java
// In MicroVMValidatingWebhook.validateNamespacePermission()
if (!namespace.getMetadata().getLabels().containsKey("lambda.aws.amazon.com/manage-microvms")) {
    errors.add("Namespace '" + name + "' is not managed — add label " +
               "'lambda.aws.amazon.com/manage-microvms=true' to enable MicroVMs");
}
```

## Helm Chart Integration

The Helm chart will:
1. Label `default` namespace at install time (via a pre-install Job or `helm install --post-renderer`)
2. Accept a `watchNamespaces` value to label additional namespaces at install time
3. Document the label requirement in the NOTES.txt post-install message

```yaml
# values.yaml
watchNamespaces:
  - default   # labelled automatically at install time
```

## Comparison with Alternatives

| Approach | Watch scope | Finalizer risk | Config overhead |
|----------|-------------|----------------|-----------------|
| **Label selector (chosen)** | Only labelled namespaces | None — unwatched = no finalizers | Label per namespace |
| Explicit namespace list | Configured namespaces only | None | Edit operator config |
| Watch all + annotation gate | All namespaces | Yes — finalizers in unlabelled ns | Annotation per namespace |
| Watch all, no gate | All namespaces | Yes | None |

## Relationship to ValidatingWebhookConfiguration

The `ValidatingWebhookConfiguration` uses a `namespaceSelector` to only invoke
the webhook in labelled namespaces:

```yaml
webhooks:
- name: validate.microvms.lambda.aws.amazon.com
  namespaceSelector:
    matchLabels:
      lambda.aws.amazon.com/manage-microvms: "true"
  failurePolicy: Fail
```

This means:
- Unlabelled namespaces: webhook not called, CRs admitted (but reconciler won't watch them)
- Labelled namespaces: webhook enforced, reconciler watches

## Feature Branch

`feature/namespace-label-selector` — implement JOSDK label selector on all
reconcilers, update webhook namespaceSelector in kubernetes.yml, add Helm
chart namespace labelling, update annotation check to label check in webhook.

## Notes

- JOSDK namespace label selector is configured per controller — all 5 reconcilers
  (MicroVM, MicroVMImage, MicroVMNetwork, MicroVMReplicaSet, MicroVMPool) must
  be configured consistently
- The operator's own namespace (`kube-microvm`) does NOT need the label — it's
  the operator's deployment namespace, not a user workload namespace
- `kube-system`, `kube-public`, `cert-manager` etc. are automatically excluded
  since they won't have the label
