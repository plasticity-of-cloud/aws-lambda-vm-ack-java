package ai.codriverlabs.microvm.operator.controller.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class AwsConnectivityHealthCheck implements HealthCheck {

    private volatile boolean awsConnectivityConfirmed = false;
    private volatile boolean informerCachesSynced = false;

    @Override
    public HealthCheckResponse call() {
        boolean ready = awsConnectivityConfirmed && informerCachesSynced;
        return HealthCheckResponse.named("aws-connectivity")
                .status(ready)
                .withData("awsConnectivity", awsConnectivityConfirmed)
                .withData("informerCachesSynced", informerCachesSynced)
                .build();
    }

    public void setAwsConnectivityConfirmed(boolean confirmed) {
        this.awsConnectivityConfirmed = confirmed;
    }

    public void setInformerCachesSynced(boolean synced) {
        this.informerCachesSynced = synced;
    }
}
