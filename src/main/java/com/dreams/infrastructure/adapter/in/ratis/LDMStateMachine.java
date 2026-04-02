package com.dreams.infrastructure.adapter.in.ratis;

import com.google.protobuf.InvalidProtocolBufferException;
import com.dreams.application.port.ConsensusHandler;
import com.dreams.application.port.LeaderChangeHandler;
import com.dreams.application.port.MigrationMachine;
import com.dreams.application.port.MigrationService;
import com.dreams.application.service.ServiceHealthMonitor;
import com.dreams.domain.model.MigrationAction;
import com.dreams.infrastructure.adapter.in.websocket.DashboardWebSocket;
import com.dreams.infrastructure.config.ActorSystemManager;
import com.dreams.infrastructure.mapper.MigrationMapper;
import com.dreams.infrastructure.serialization.protobuf.MigrationActionOuterClass;
import com.dreams.shared.constants.KeyFigureEnum;
import com.dreams.shared.constants.MessageTypeEnum;
import io.smallrye.mutiny.Uni;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftGroupMemberId;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.raftlog.RaftLog;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
public class LDMStateMachine extends BaseStateMachine implements MigrationMachine<LDMStateMachine> {

    private final ConsensusHandler consensusHandler;
    private final MigrationService migrationService;

    @Getter
    private final LeaderChangeHandler<RaftGroupMemberId, RaftPeerId, RaftServer, RaftGroupId> leaderChangeHandler;

    private final MigrationMapper migrationMapper;

    private final ServiceHealthMonitor microservicesCache;

    private final DashboardWebSocket dashboardWebSocket;

    private static final AtomicLong leaderChangeCount = new AtomicLong(0L);

    private static volatile String currentLeader = "N/A";

    public static long getLeaderChangeCount() { return leaderChangeCount.get(); }
    public static String getCurrentLeader() { return currentLeader; }

    /**
     * Processes migration actions
     */
    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        return Uni.createFrom()
                .item(trx)
                .onItem()
                .transformToUni(transactionContext -> {
                    RaftProtos.LogEntryProto entry = transactionContext.getLogEntry();
                    // Parse transaction data
                    byte[] data = entry.getStateMachineLogEntry().getLogData().toByteArray();
                    log.info("Applying transaction with LogIndex: {}", entry.getIndex());
                    super.applyTransaction(transactionContext);

                    // Map to domain model
                    MigrationActionOuterClass.MigrationAction protoMigrationAction;
                    try {
                        protoMigrationAction = MigrationActionOuterClass.MigrationAction.parseFrom(data);
                        log.debug("RECEIVED protoMigrationAction: {}", protoMigrationAction);
                    } catch (InvalidProtocolBufferException e) {
                        return Uni.createFrom().failure(e);
                    }
                    MigrationAction migrationAction = this.migrationMapper.toDomainModel(protoMigrationAction);
                    log.debug("RECEIVED migrationAction: {}", migrationAction);

                    // Handle consensus and execute migration
                    return this.consensusHandler.handle(migrationAction)
                            .onItem()
                            .transform(unused -> {
                                log.info("Transaction processed successfully for LogIndex: {}", entry.getIndex());
                                if (trx.getServerRole() == RaftProtos.RaftPeerRole.LEADER) {
                                    this.migrationService.executeMigration(migrationAction);
                                    log.debug("--------->> Microservices Cache AFTER proposal as LEADER: {}", microservicesCache);
                                    microservicesCache.outputCache();
                                    this.leaderChangeHandler.getActorSystemsManager().getActorSystem().tell(new ActorSystemManager.PerformMigrationAction(migrationAction));
                                    return Message.valueOf("Transaction applied successfully for migractionAction: " + migrationAction);
                                }
                                log.debug("--------->> Microservices Cache AFTER proposal: {}", microservicesCache);
                                microservicesCache.outputCache();

                                this.leaderChangeHandler.getActorSystemsManager().getActorSystem().tell(new ActorSystemManager.PerformMigrationAction(migrationAction));

                                return Message.valueOf("Transaction applied without leader-specific actions.");
                            });
                })
                .onFailure()
                .recoverWithItem(throwable -> Message.valueOf("Error processing transaction: " + throwable.getMessage()))
                .subscribeAsCompletionStage();
    }

    // Leader-specific logic extracted for clarity
    @Override
    public CompletableFuture handleMigrationAction(RaftServer raftServer, MigrationActionOuterClass.MigrationAction protoMigrationAction) {
        return this.leaderChangeHandler.triggerLeaderChange(raftServer, this.getId(), this.getGroupId())
                .map(unused -> {
                    ByteString successMessageBytes = ByteString.copyFromUtf8("<<Transaction applied successfully>> : ");
                    ByteString migrationActionBytes = ByteString.copyFrom(protoMigrationAction.toByteArray());
                    return Message.valueOf(successMessageBytes.concat(migrationActionBytes));
                })
                .convert().toCompletionStage()
                .toCompletableFuture();
    }

    @Override
    public LDMStateMachine getLDMStateMachine() {
        return this;
    }

    /**
     * Handles leader changes and delegates to RaftLeaderChangedHandler.
     */
    @Override
    public void notifyLeaderChanged(RaftGroupMemberId groupMemberId, RaftPeerId newLeaderId) {
        log.info("Leader change detected: New leader is {}", newLeaderId);
        currentLeader = newLeaderId.toString();
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        jsonObjectBuilder.add("NEW_LEADER", newLeaderId.toString());

        jsonObjectBuilder.add(KeyFigureEnum.LEADER_CHANGE_COUNT.toString(), leaderChangeCount.incrementAndGet());
        log.debug(KeyFigureEnum.LEADER_CHANGE_COUNT + ": " + leaderChangeCount.get());

        this.dashboardWebSocket.broadcast(MessageTypeEnum.LEADER_CHANGED, jsonObjectBuilder.build());
        try {
            // Delegate the handling of the leader change to RaftLeaderChangedHandler
            leaderChangeHandler.handleLeaderChangedEvent(groupMemberId, newLeaderId);
            log.info("Leader change handled successfully by RaftLeaderChangedHandler");
        } catch (Exception e) {
            log.error("Error handling leader change to new leader: {}", newLeaderId, e);
        }
    }

    public List<RaftProtos.LogEntryProto> readAllRaftLogEntries() throws Exception {
        RaftGroupId groupId = getGroupId();
        RaftLog raftLog = getServer().get().getDivision(groupId).getRaftLog();

        if (raftLog == null) {
            throw new IllegalStateException("RaftLog is not initialized");
        }

        List<RaftProtos.LogEntryProto> logEntries = new ArrayList<>();
        long startIndex = raftLog.getStartIndex();
        long endIndex = raftLog.getLastCommittedIndex();

        log.info("Reading Raft log entries from index " + startIndex + " to " + endIndex);

        for (long index = startIndex; index <= endIndex; index++) {
            RaftProtos.LogEntryProto entry = raftLog.get(index);
            if (entry != null) {
                logEntries.add(entry);
            } else {
                log.info("No entry found at index: " + index);
            }
        }

        return logEntries;
    }
}

