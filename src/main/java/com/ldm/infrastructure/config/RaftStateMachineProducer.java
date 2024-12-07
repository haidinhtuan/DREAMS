package com.ldm.infrastructure.config;

import com.ldm.application.port.MigrationService;
import com.ldm.application.service.DefaultConsensusHandler;
import com.ldm.application.service.DomainManager;
import com.ldm.application.service.MicroservicesCache;
import com.ldm.infrastructure.adapter.in.ratis.RaftLeaderChangeHandler;
import com.ldm.infrastructure.adapter.in.ratis.RaftStateMachine;
import com.ldm.infrastructure.mapper.MigrationMapper;
import com.ldm.shared.constants.LeaderElectionModeEnum;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.ratis.client.RaftClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class RaftStateMachineProducer {
    @Inject
    DomainManager domainManager;
    @Inject
    MigrationService migrationService;

    @Inject
    RaftClient raftClient;
    @Inject
    MigrationMapper migrationMapper;

    @Inject
    ActorSystemManager actorSystemManager;

    @Inject
    MicroservicesCache microservicesCache;

    @ConfigProperty(name = "leader.election.mode")
    LeaderElectionModeEnum leaderElectionMode;

    @ConfigProperty(name = "leader.election.default-leader")
    String defaultLeader;

    @Produces
    @Singleton
    public RaftStateMachine createRaftStateMachine() {
        RaftLeaderChangeHandler raftLeaderChangeHandler = new RaftLeaderChangeHandler(raftClient, domainManager, actorSystemManager);
        raftLeaderChangeHandler.setLeaderElectionModeEnum(leaderElectionMode);
        raftLeaderChangeHandler.setDefaultLeader(defaultLeader);

        return new RaftStateMachine(
                new DefaultConsensusHandler(domainManager),
                migrationService,
                raftLeaderChangeHandler,
                migrationMapper, microservicesCache);
    }
}
