package com.dreams.infrastructure.config;

import com.dreams.application.port.ClusterMonitoringService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class SystemStarter {

    @Inject
    Instance<ClusterMonitoringService> clusterMonitoringService;

    @Inject
    RaftServerManager raftServerManager;

    public void onStart(@Observes StartupEvent event) {
        clusterMonitoringService.get().getMicroservicesFromCluster();

        raftServerManager.startRaftServer();

        log.info("Application fully started with @Observes StartupEvent");
    }
}
