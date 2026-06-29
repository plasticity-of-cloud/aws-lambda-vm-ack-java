package ai.codriverlabs.microvm.operator.core.model;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.*;

@Group("lambda.aws.amazon.com")
@Version("v1alpha1")
@Kind("MicroVMNetwork")
@Singular("microvmnetwork")
@Plural("microvmnetworks")
public class MicroVMNetwork extends CustomResource<MicroVMNetworkSpec, MicroVMNetworkStatus> implements Namespaced {
}
