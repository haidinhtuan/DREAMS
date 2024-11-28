package com.ldm.infrastructure.adapter.in.ratis;

import com.ldm.application.port.ConsensusHandler;
import com.ldm.application.port.MigrationService;
import com.ldm.domain.model.MigrationAction;
import com.ldm.infrastructure.mapper.MigrationMapper;
import com.ldm.infrastructure.serialization.protobuf.MigrationActionOuterClass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftGroupMemberId;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.raftlog.RaftLog;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public class RaftStateMachine extends BaseStateMachine {

    private final ConsensusHandler consensusHandler;
    private final MigrationService migrationService;

    private final RaftLeaderChangedHandler raftLeaderChangedHandler;

    private final MigrationMapper migrationMapper;

    /**
     * Processes migration actions
     */
    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        RaftProtos.LogEntryProto entry = trx.getLogEntry();
        // Process migration proposals or other messages
        try {
            byte[] data = entry.getStateMachineLogEntry().getLogData().toByteArray();
            log.info("Applying transaction with LogIndex: {}", entry.getIndex());
            super.applyTransaction(trx);

            MigrationActionOuterClass.MigrationAction protoMigrationAction = MigrationActionOuterClass.MigrationAction.parseFrom(data);
            MigrationAction migrationAction = this.migrationMapper.toDomainModel(protoMigrationAction);

            this.consensusHandler.handle(migrationAction);
            log.info("Transaction processed successfully for LogIndex: {}", entry.getIndex());

            RaftProtos.RaftPeerRole role = trx.getServerRole();
            if(role== RaftProtos.RaftPeerRole.LEADER) {
                log.info("This LDM is curerently the leader!");
                this.migrationService.executeMigration(migrationAction);
            }

            // Convert the success message to ByteString directly
            ByteString successMessageBytes = ByteString.copyFromUtf8("<<Transaction applied successfully>> : ");
            ByteString migrationActionBytes = ByteString.copyFrom(protoMigrationAction.toByteArray());

            // Combine both ByteStrings efficiently
            ByteString migrationSuccessResponse = successMessageBytes.concat(migrationActionBytes);

            // Return the combined message wrapped in a CompletableFuture
            return CompletableFuture.completedFuture(Message.valueOf(migrationSuccessResponse));

        } catch (Exception e) {
            log.error("Failed to process transaction at LogIndex: {}", entry.getIndex(), e);
            // Return a failed CompletableFuture with an error message
            return CompletableFuture.completedFuture(Message.valueOf("Error processing transaction: " + e.getMessage()));
        }
    }

    /**
     * Handles leader changes and delegates to RaftLeaderChangedHandler.
     */
    @Override
    public void notifyLeaderChanged(RaftGroupMemberId groupMemberId, RaftPeerId newLeaderId) {
        log.info("Leader change detected: New leader is {}", newLeaderId);
        try {
            // Delegate the handling of the leader change to RaftLeaderChangedHandler
            raftLeaderChangedHandler.handleLeaderChangedEvent(groupMemberId, newLeaderId);
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

