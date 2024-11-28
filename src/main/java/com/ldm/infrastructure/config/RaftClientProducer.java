package com.ldm.infrastructure.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.protocol.RaftGroup;

@ApplicationScoped
public class RaftClientProducer {

    @Inject
    RaftClusterConfig raftClusterConfig;

    @Produces
    @ApplicationScoped
    public RaftClient createRaftClient() {
        RaftGroup raftGroup = RaftServerManager.getRaftGroup(raftClusterConfig);

        // Initialize Raft properties (you can configure them as needed)
        RaftProperties raftProperties = new RaftProperties();

        // Build and return the Ratis client
        return RaftClient.newBuilder()
                .setProperties(raftProperties)
                .setRaftGroup(raftGroup)
                .build();
    }
}
