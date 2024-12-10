package com.ldm.infrastructure.adapter.in.ratis;

import com.ldm.application.port.ConsensusHandler;
import com.ldm.application.port.LeaderChangeHandler;
import com.ldm.application.port.MigrationMachine;
import com.ldm.application.port.MigrationService;
import com.ldm.application.service.MicroservicesCache;
import com.ldm.domain.model.Microservice;
import com.ldm.domain.model.MigrationAction;
import com.ldm.infrastructure.mapper.MigrationMapper;
import com.ldm.infrastructure.serialization.protobuf.MigrationActionOuterClass;
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

@Slf4j
@RequiredArgsConstructor
public class LDMStateMachine extends BaseStateMachine implements MigrationMachine<LDMStateMachine> {

    private final ConsensusHandler consensusHandler;
    private final MigrationService migrationService;

    @Getter
    private final LeaderChangeHandler<RaftGroupMemberId, RaftPeerId, RaftServer, RaftGroupId> leaderChangeHandler;

    private final MigrationMapper migrationMapper;

    private final MicroservicesCache microservicesCache;

    /**
     * Processes migration actions
     */

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        RaftProtos.LogEntryProto entry = trx.getLogEntry();

        try {
            // Parse transaction data
            byte[] data = entry.getStateMachineLogEntry().getLogData().toByteArray();
            log.info("Applying transaction with LogIndex: {}", entry.getIndex());
            super.applyTransaction(trx);

            // Map to domain model
            MigrationActionOuterClass.MigrationAction protoMigrationAction = MigrationActionOuterClass.MigrationAction.parseFrom(data);
            MigrationAction migrationAction = this.migrationMapper.toDomainModel(protoMigrationAction);

            // Handle consensus and execute migration
            this.consensusHandler.handle(migrationAction);
            log.info("Transaction processed successfully for LogIndex: {}", entry.getIndex());

            if (trx.getServerRole() == RaftProtos.RaftPeerRole.LEADER) {
                log.info("This LDM is currently the leader!");
                this.migrationService.executeMigration(migrationAction);
                Microservice migratedMicroservice = new Microservice(migrationAction.microservice().getId(), migrationAction.microservice().getName()
                        , migrationAction.microservice().isNonMigratable(), migrationAction.targetK8sCluster(), migrationAction.microservice().getAffinities(),
                        migrationAction.microservice().getDataExchangedWithServices(), migrationAction.microservice().getCpuUsage(), migrationAction.microservice().getMemoryUsage());

                log.info("Updating the microservices cache after migrationAction: {}", migrationAction);
                this.microservicesCache.cacheMicroservice(migratedMicroservice.getId(), migratedMicroservice);

                this.microservicesCache.getAllMicroservices().forEach(microservice -> {
                    Double affinity = microservice.getAffinities().get(migratedMicroservice);
                    if (affinity != null) {
                        // Update microservice on affinity map with correct key-value pair
                        microservice.getAffinities().put(migratedMicroservice, affinity);
                        this.microservicesCache.cacheMicroservice(microservice.getId(), microservice);
                        log.debug("Updated affinity map of microservice {} with migrated microservice {}", microservice, migratedMicroservice);
                    }
                });

                log.info("Microservices Cache AFTER proposal: {}", microservicesCache);

                return this.getServer()
                        .thenCompose(raftServer -> handleMigrationAction(raftServer, protoMigrationAction));
            }

            // Default response for non-leader nodes
            return CompletableFuture.completedFuture(Message.valueOf("Transaction applied without leader-specific actions."));

        } catch (Exception e) {
            log.error("Failed to process transaction at LogIndex: {}", entry.getIndex(), e);

            // Centralized error handling
            return CompletableFuture.completedFuture(Message.valueOf("Error processing transaction: " + e.getMessage()));
        }
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

        System.out.println("Reading Raft log entries from index " + startIndex + " to " + endIndex);

        for (long index = startIndex; index <= endIndex; index++) {
            RaftProtos.LogEntryProto entry = raftLog.get(index);
            if (entry != null) {
                logEntries.add(entry);
            } else {
                System.out.println("No entry found at index: " + index);
            }
        }

        return logEntries;
    }
}

