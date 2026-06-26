package ai.codriverlabs.microvm.operator.core.model;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("lambda.aws.amazon.com")
@Version("v1alpha1")
public class MicroVMImage extends CustomResource<MicroVMImageSpec, MicroVMImageStatus> implements Namespaced {
}
