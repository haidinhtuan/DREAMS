package com.dreams.infrastructure.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.ratis.RaftConfigKeys;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.rpc.SupportedRpcType;

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
        RaftConfigKeys.Rpc.setType(raftProperties, SupportedRpcType.GRPC);
        GrpcConfigKeys.Server.setHost(raftProperties, raftClusterConfig.server().host());
        GrpcConfigKeys.Server.setPort(raftProperties, raftClusterConfig.server().port());

        // Build and return the Ratis client
        return RaftClient.newBuilder()
                .setProperties(raftProperties)
                .setRaftGroup(raftGroup)
                .build();
    }
}
