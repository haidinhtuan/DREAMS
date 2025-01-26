package com.ldm.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldm.application.port.MigrationMachine;
import com.ldm.application.port.MigrationService;
import com.ldm.application.service.*;
import com.ldm.infrastructure.adapter.in.ratis.LDMStateMachine;
import com.ldm.infrastructure.adapter.in.ratis.RaftLeaderChangeHandler;
import com.ldm.infrastructure.adapter.in.websocket.DashboardWebSocket;
import com.ldm.infrastructure.mapper.MicroserviceMapper;
import com.ldm.infrastructure.mapper.MigrationMapper;
import com.ldm.shared.constants.LeaderElectionModeEnum;
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
    private final ClusterLatencyCache clusterLatencyCache;
    private final RaftClient raftClient;

    private final DomainManager domainManager;

    private final MigrationMapper migrationMapper;

    private final MicroserviceMapper microserviceMapper;

    @Inject
    MicroservicesCache microservicesCache;

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

    @Getter
    @Setter
    private MigrationMachine<LDMStateMachine> migrationMachine;


    @Produces
    @Singleton
    public ActorSystemManager createActorSystemsManager() {
        ActorSystemManager actorSystemManager = new ActorSystemManager(ldmConfig, clusterLatencyCache, raftClient, domainManager, migrationMapper, microserviceMapper, objectMapper, ldmStateService, dashboardWebSocket, datasourceConfig);
        RaftLeaderChangeHandler raftLeaderChangeHandler = new RaftLeaderChangeHandler(raftClient, domainManager, actorSystemManager);
        raftLeaderChangeHandler.setLeaderElectionModeEnum(leaderElectionMode);
        raftLeaderChangeHandler.setDefaultLeader(defaultLeader);

        LDMStateMachine ldmStateMachine = new LDMStateMachine(
                new DefaultConsensusHandler(domainManager),
                migrationService,
                raftLeaderChangeHandler,
                migrationMapper,
                microservicesCache);

        actorSystemManager.setMigrationMachine(ldmStateMachine);

        actorSystemManager.init();

        return actorSystemManager;
    }
}
