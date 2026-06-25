package com.amazonaws.lambda.operator.core.model;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.*;

@Group("lambda.aws.amazon.com")
@Version("v1alpha1")
@Kind("MicroVM")
@Singular("microvm")
@Plural("microvms")
@ShortNames("mvm")
public class MicroVM extends CustomResource<MicroVMSpec, MicroVMStatus> implements Namespaced {
}
