package ai.codriverlabs.microvm.operator.controller.config;

import ai.codriverlabs.microvm.operator.core.state.MicroVMStateMachine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer for operator-core beans that don't carry CDI annotations themselves
 * (since operator-core is framework-agnostic).
 */
@ApplicationScoped
public class CoreBeansProducer {

    @Produces
    @ApplicationScoped
    public MicroVMStateMachine microVMStateMachine() {
        return new MicroVMStateMachine();
    }
}
