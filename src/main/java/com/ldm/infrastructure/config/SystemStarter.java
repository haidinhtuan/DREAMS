package com.ldm.infrastructure.config;

import com.ldm.application.port.ClusterMonitoringService;
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
    private final ActorSystemManager actorSystemManager;

    public void onStart(@Observes StartupEvent event) {
        clusterMonitoringService.getMicroservicesFromCluster();

        raftServerManager.startRaftServer();

//        actorSystemManager.init();
        log.info("Application fully started with @Observes StartupEvent");
    }
}
