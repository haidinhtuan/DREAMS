package com.dreams.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dreams.application.port.MigrationMachine;
import com.dreams.application.port.MigrationService;
import com.dreams.application.service.*;
import com.dreams.infrastructure.adapter.in.ratis.LDMStateMachine;
import com.dreams.infrastructure.adapter.in.ratis.LeaderCoordinator;
import com.dreams.infrastructure.adapter.in.websocket.DashboardWebSocket;
import com.dreams.infrastructure.mapper.MicroserviceMapper;
import com.dreams.infrastructure.mapper.MigrationMapper;
import com.dreams.shared.constants.LeaderElectionModeEnum;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.ratis.client.RaftClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@RequiredArgsConstructor
public class ActorSystemsManagerProducer {

    private final LdmConfig ldmConfig;
    private final InterDomainLatencyMonitor clusterLatencyCache;
    private final RaftClient raftClient;

    private final MigrationEligibilityEvaluator domainManager;

    private final MigrationMapper migrationMapper;

    private final MicroserviceMapper microserviceMapper;

    @Inject
    ServiceHealthMonitor microservicesCache;

    @Inject
    MigrationService migrationService;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "leader.election.mode")
    LeaderElectionModeEnum leaderElectionMode;

    @ConfigProperty(name = "leader.election.default-leader")
    String defaultLeader;

    @Inject
    DatasourceConfig datasourceConfig;

    @Inject
    LdmStateService ldmStateService;

    @Inject
    DashboardWebSocket dashboardWebSocket;

    @Inject
    MetricsAggregator measurementService;

    @Getter
    @Setter
    private MigrationMachine<LDMStateMachine> migrationMachine;


    @Produces
    @Singleton
    public ActorSystemManager createActorSystemsManager() {
        ActorSystemManager actorSystemManager = new ActorSystemManager(ldmConfig, clusterLatencyCache, raftClient, domainManager, migrationMapper, microserviceMapper, objectMapper, ldmStateService, dashboardWebSocket, measurementService, datasourceConfig);
        LeaderCoordinator raftLeaderChangeHandler = new LeaderCoordinator(raftClient, domainManager, actorSystemManager);
        raftLeaderChangeHandler.setLeaderElectionModeEnum(leaderElectionMode);
        raftLeaderChangeHandler.setDefaultLeader(defaultLeader);

        LDMStateMachine ldmStateMachine = new LDMStateMachine(
                new RaftConsensusHandler(domainManager),
                migrationService,
                raftLeaderChangeHandler,
                migrationMapper,
                microservicesCache, dashboardWebSocket);

        actorSystemManager.setMigrationMachine(ldmStateMachine);

        actorSystemManager.init();

        return actorSystemManager;
    }
}
