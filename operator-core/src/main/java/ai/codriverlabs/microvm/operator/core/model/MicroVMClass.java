package ai.codriverlabs.microvm.operator.core.model;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * MicroVMClass defines a named runtime profile (idle policy, networking, sizing) that
 * can be applied to MicroVM instances by setting spec.className.
 *
 * Analogous to Kubernetes StorageClass / IngressClass — admins define classes,
 * developers reference them. className is optional; without it, MicroVM uses its own spec values.
 */
@Group("lambda.aws.amazon.com")
@Version("v1alpha1")
public class MicroVMClass extends CustomResource<MicroVMClassSpec, Void> implements Namespaced {
}
