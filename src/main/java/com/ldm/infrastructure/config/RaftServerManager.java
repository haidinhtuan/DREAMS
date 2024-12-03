package com.ldm.infrastructure.config;

import com.ldm.infrastructure.adapter.in.ratis.RaftStateMachine;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.RaftConfigKeys;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.rpc.SupportedRpcType;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.server.storage.RaftStorage;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
@RequiredArgsConstructor
@Slf4j
public class RaftServerManager {

    @Getter
    private RaftServer server;

    private final RaftClient raftClient;
//    private final DomainManager domainManager;
//
//    private final ActorSystemManager actorSystemManager;

    @Getter
    private final LdmConfig ldmConfig;
    private final RaftClusterConfig raftClusterConfig;

    private final RaftStateMachine raftStateMachine;

//    private final MigrationMapper migrationMapper;
//
//    private final MigrationService migrationService;

    void startRaftServer() {
        try {
            log.info("Raft Client Storage Dir: {}", raftClusterConfig.server().storage().dir());
            log.info("Test Data File: {}", ConfigProvider.getConfig().getValue("testdata.file", String.class));
            log.info("LDM ID: {}", raftClusterConfig.server().id());

            startServer();
        } catch (Exception e) {
            log.error("Raft Server could NOT be started for LDM ID: {}. Please check configuration and logs for details.", ldmConfig.id(), e);
            throw new RuntimeException("Error starting Raft Server for LDM ID: " + ldmConfig.id(), e);
        }
    }

    public void startServer() throws IOException {
        RaftGroup raftGroup = getRaftGroup(raftClusterConfig);
        Path storageDir = createStorageDirectory();

        RaftStorage.StartupOption startupOption;
        // Check if the directory is already initialized
        if (isStorageAlreadyFormatted(storageDir)) {
            log.info("Raft storage directory is already formatted: {}", storageDir);
            startupOption = RaftStorage.StartupOption.RECOVER; // Recover from potential inconsistencies
        } else {
            log.info("Formatting Raft storage directory: {}", storageDir);
            startupOption = RaftStorage.StartupOption.FORMAT;
        }

        this.server = RaftServer.newBuilder()
                .setServerId(RaftPeerId.valueOf(ldmConfig.id()))
                .setOption(startupOption)
                .setGroup(raftGroup)
                .setStateMachine(raftStateMachine)
                .setProperties(buildRaftProperties(storageDir))
                .build();

        server.start();
        log.info("Raft Server started for LDM ID: {} with group: {}", ldmConfig.id(), raftGroup);
    }

    private boolean isStorageAlreadyFormatted(Path storageDir) {
        File dir = storageDir.toFile();
        File raftMetaFile = new File(dir, raftClusterConfig.cluster().id()+"/current");
        return raftMetaFile.exists();
    }

    private RaftProperties buildRaftProperties(Path storageDir) {
        RaftProperties properties = new RaftProperties();
        RaftConfigKeys.Rpc.setType(properties, SupportedRpcType.GRPC);
        GrpcConfigKeys.Server.setHost(properties, raftClusterConfig.server().host());
        GrpcConfigKeys.Server.setPort(properties, raftClusterConfig.server().port());
//        properties.set("raft.server.port", String.valueOf(raftClusterConfig.server().port()));
//        properties.set("ratis.metric.registry.impl", "null");

        RaftServerConfigKeys.setStorageDir(properties, Collections.singletonList(storageDir.toFile()));
        return properties;
    }

    private Path createStorageDirectory() {
        Path storageDir = Paths.get(System.getProperty("user.dir"))
                .resolve(raftClusterConfig.server().storage().dir())
                .resolve(raftClusterConfig.server().id());
        File storageDirFile = storageDir.toFile();

        if (!storageDirFile.exists() && !storageDirFile.mkdirs()) {
            log.error("Failed to create storage directory at path: {}", storageDir);
        }
        return storageDir;
    }

    static RaftGroup getRaftGroup(RaftClusterConfig raftClusterConfig) {
        Set<RaftPeer> peers = raftClusterConfig.cluster().peers().stream()
                .map(peer -> RaftPeer.newBuilder()
                        .setId(peer.id())
                        .setAddress(peer.host()+":"+peer.port())
//                        .setAddress(NetUtils.createSocketAddr(peer.host(), peer.port()))
                        .build())
                .collect(Collectors.toSet());

        return RaftGroup.valueOf(RaftGroupId.valueOf(UUID.fromString(raftClusterConfig.cluster().id())), peers);
    }

    void onStop(@Observes ShutdownEvent ev) {
        closeRaftClient();
        closeRaftServer();
    }

    private void closeRaftServer() {
        if (server != null) {
            try {
                server.close();
                log.info("Raft Server closed for LDM ID: {}", ldmConfig.id());
            } catch (IOException e) {
                log.error("Failed to close Raft Server for LDM ID: {}", ldmConfig.id(), e);
            }
        }
    }

    private void closeRaftClient() {
        if (raftClient != null) {
            try {
                raftClient.close();
                log.info("Raft Client closed successfully.");
            } catch (IOException e) {
                log.error("Failed to close Raft Client.", e);
            }
        }
    }
}
