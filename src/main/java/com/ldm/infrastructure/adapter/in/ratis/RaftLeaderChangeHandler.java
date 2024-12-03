package com.ldm.infrastructure.adapter.in.ratis;

import com.ldm.application.service.DomainManager;
import com.ldm.infrastructure.config.ActorSystemManager;
import com.ldm.shared.constants.LeaderElectionModeEnum;
import com.ldm.shared.util.ApplicationUtils;
import io.quarkus.runtime.util.StringUtil;
import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.*;
import org.apache.ratis.server.RaftServer;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Slf4j
public class RaftLeaderChangeHandler {

    private final RaftClient raftClient;
    private final DomainManager domainManager;
    private final ActorSystemManager actorSystemManager;

    private boolean isLeader = false;  // Track whether this node is currently the leader

    @Setter
    private LeaderElectionModeEnum leaderElectionModeEnum;

    @Setter
    private String defaultLeader;


    public void handleLeaderChangedEvent(RaftGroupMemberId groupMemberId, RaftPeerId newLeaderId) {
        if (newLeaderId.equals(groupMemberId.getPeerId())) {
            if (!isLeader) {
                // This node just became the leader
                log.info("This node is now the leader. Starting QoS Improvement Proposal scheduling.");
                isLeader = true;
                startQoSImprovementSuggester();
            } else {
                log.info("This node is already the leader. No action needed.");
            }
        } else if (isLeader) {
            // This node was the leader but no longer is
            log.info("This node has lost leadership. Stopping QoS Improvement Proposal scheduling.");
            isLeader = false;
            stopQoSImprovementSuggester();
        } else {
            log.info("This node is not the leader. No action taken.");
        }
    }

    private void startQoSImprovementSuggester() {
        // Send command to ActorSystemManager to start QoSImprovementSuggester
        actorSystemManager.getActorSystem().tell(new ActorSystemManager.StartQoSImprovementSuggester(raftClient, domainManager));
        log.info("QoSImprovementSuggester actor started successfully.");
    }

    private void stopQoSImprovementSuggester() {
        // Here we would stop the QoSImprovementSuggester actor or send a stop message
        log.info("Sending stop command to QoSImprovementSuggester actor.");
        actorSystemManager.getActorSystem().tell(new ActorSystemManager.StopQoSImprovementSuggester());
    }

//    public Uni<Void> triggerLeaderChange(RaftServer raftServer, RaftPeerId raftPeerId, RaftGroupId raftGroupId){
//        TransferLeadershipRequest request = new TransferLeadershipRequest(ClientId.randomId(), raftPeerId,
//                raftGroupId, System.currentTimeMillis(), null, 5000);
//        if(leaderElectionModeEnum == LeaderElectionModeEnum.TESTING) {
//            GroupInfoRequest groupInfoRequest = new GroupInfoRequest(ClientId.randomId(), raftPeerId, raftGroupId, System.currentTimeMillis());
//            try {
//                GroupInfoReply groupInfo = raftServer.getGroupInfo(groupInfoRequest);
//                if(!StringUtil.isNullOrEmpty(defaultLeader)) {
//                    log.info(">> Default Leader elected: " + defaultLeader);
//                    RaftPeer peer = groupInfo.getGroup().getPeer(RaftPeerId.valueOf(defaultLeader));
//
//                    request = new TransferLeadershipRequest(ClientId.randomId(), raftPeerId,
//                            raftGroupId, System.currentTimeMillis(), peer.getId(), 5000);
//                }
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//
//        }
//        return Uni.createFrom().completionStage(safeTransferLeadershipAsync(raftServer, request));
//    }

    public Uni<Void> triggerLeaderChange(
            RaftServer raftServer,
            RaftPeerId raftPeerId,
            RaftGroupId raftGroupId
    ) {
        TransferLeadershipRequest request = ApplicationUtils.createTransferLeadershipRequest(
                raftPeerId, raftGroupId, null
        );

        if (LeaderElectionModeEnum.TESTING == leaderElectionModeEnum) {
//                GroupInfoReply groupInfo = ApplicationUtils.fetchGroupInfo(raftServer, raftPeerId, raftGroupId);
            if (!StringUtil.isNullOrEmpty(defaultLeader)) {
                log.info(">> Set to Default Leader: " + defaultLeader);
//                    RaftPeer defaultLeaderPeer = groupInfo.getGroup().getPeer(RaftPeerId.valueOf(defaultLeader));
                request = ApplicationUtils.createTransferLeadershipRequest(raftPeerId, raftGroupId, RaftPeerId.valueOf(defaultLeader));
            }
        }

        return Uni.createFrom().completionStage(safeTransferLeadershipAsync(raftServer, request));
    }


    private CompletableFuture<Void> safeTransferLeadershipAsync(RaftServer raftServer, TransferLeadershipRequest request) {
        try {
            return raftServer
                    .transferLeadershipAsync(request) // Returns CompletableFuture<RaftClientReply>
                    .thenApply(reply -> {
                        if (!reply.isSuccess()) {
                            log.error("Leadership transfer failed: " + reply.getException());
                        } else {
                            log.info("Leadership transfer response received: {}", reply);
                        }
                        return null; // Transform to CompletableFuture<Void>
                    });
        } catch (IOException e) {
            log.error("IOException during transferLeadershipAsync for peer {} and group {}: ",
                    request.getServerId(), request.getRaftGroupId(), e);

            CompletableFuture<Void> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Failed to initiate leadership transfer", e));
            return failedFuture;
        }
    }

}
