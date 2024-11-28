package com.ldm.application.service;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ldm.application.port.RaftStorageService;
import com.ldm.domain.model.MigrationAction;
import com.ldm.infrastructure.adapter.in.ratis.RaftStateMachine;
import com.ldm.infrastructure.mapper.MigrationMapper;
import com.ldm.infrastructure.serialization.protobuf.MigrationActionOuterClass;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.proto.RaftProtos;

import java.util.List;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RaftStorageServiceImpl implements RaftStorageService {

    private final RaftStateMachine raftStateMachine;

    private final MigrationMapper migrationMapper;

    /**
     * Retrieves all approved migration actions from the Raft storage.
     *
     * @return List of MigrationAction objects representing approved migrations.
     */
   @Override
    public Multi<MigrationAction> getApprovedMigrationActions() {
        try {
            // Retrieve all Raft log entries
            List<RaftProtos.LogEntryProto> logEntryProtos = this.raftStateMachine.readAllRaftLogEntries();

            // Transform log entries into domain model objects
            return Multi.createFrom().iterable(logEntryProtos)
                    .map(this::parseMigrationAction) // Extract and parse each MigrationAction
                    .map(this.migrationMapper::toDomainModel); // Convert Proto to Domain Model;
        } catch (Exception e) {
            // Wrap and rethrow any exceptions with a meaningful message
            throw new RuntimeException("Error retrieving approved migration actions from Raft logs", e);
        }
    }

    private MigrationActionOuterClass.MigrationAction parseMigrationAction(RaftProtos.LogEntryProto logEntryProto) {
        try {
            byte[] data = logEntryProto.getStateMachineLogEntry().getLogData().toByteArray();
            return MigrationActionOuterClass.MigrationAction.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            // Log and rethrow with a runtime exception to ensure visibility
            throw new RuntimeException("Failed to parse MigrationAction from Raft log entry", e);
        }
    }

}
