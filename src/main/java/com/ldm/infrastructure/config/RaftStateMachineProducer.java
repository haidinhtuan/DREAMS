package com.ldm.infrastructure.config;

import com.ldm.application.port.MigrationService;
import com.ldm.application.service.DefaultConsensusHandler;
import com.ldm.application.service.DomainManager;
import com.ldm.infrastructure.adapter.in.ratis.RaftLeaderChangeHandler;
import com.ldm.infrastructure.adapter.in.ratis.RaftStateMachine;
import com.ldm.infrastructure.mapper.MigrationMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.ratis.client.RaftClient;

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

    @Produces
    @Singleton
    public RaftStateMachine createRaftStateMachine() {
        return new RaftStateMachine(
                new DefaultConsensusHandler(domainManager),
                migrationService,
                new RaftLeaderChangeHandler(raftClient, domainManager, actorSystemManager),
                migrationMapper);
    }
}
