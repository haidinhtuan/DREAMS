package com.dreams.infrastructure.config;

import com.dreams.application.port.ClusterMonitoringService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class SystemStarter {

    private final ClusterMonitoringService clusterMonitoringService;
    private final RaftServerManager raftServerManager;

    public void onStart(@Observes StartupEvent event) {
        clusterMonitoringService.getMicroservicesFromCluster();

        raftServerManager.startRaftServer();

        log.info("Application fully started with @Observes StartupEvent");
    }
}
