package com.dreams.infrastructure.adapter.out.pekko;

import com.dreams.application.port.LeaderChangeHandler;
import com.dreams.application.service.MetricsAggregator;
import com.dreams.domain.measurement.MeasurementData;
import com.dreams.domain.model.MigrationAction;
import com.dreams.domain.model.MigrationCandidate;
import com.dreams.infrastructure.adapter.in.ratis.LDMStateMachine;
import com.dreams.infrastructure.adapter.in.websocket.DashboardWebSocket;
import com.dreams.infrastructure.mapper.MigrationMapper;
import com.dreams.infrastructure.serialization.protobuf.MigrationActionOuterClass;
import com.dreams.shared.constants.PerformanceMeasurementConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftGroupMemberId;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

@Slf4j
public class MigrationOrchestrator {

    static void sendMigrationAction(
            MigrationCandidate migrationCandidate,
            MigrationMapper migrationMapper,
            RaftClient raftClient,
            String processId,
            MetricsAggregator measurementService,
            DashboardWebSocket dashboardWebSocket

    ) {
        MigrationAction migrationAction = migrationCandidate.toMigrationAction();
        MigrationActionOuterClass.MigrationAction migrationActionProto = migrationMapper.toProto(migrationAction);
        Message migrationActionMessage = Message.valueOf(ByteString.copyFrom(migrationActionProto.toByteArray()));

        log.debug("Sending this migrationActionProto: {}", migrationActionProto);

        raftClient.async().send(migrationActionMessage)
                .thenAccept(reply -> {
                    measurementService.recordEnd(processId, System.nanoTime(), PerformanceMeasurementConstants.RESULT_APPROVED);
                    MeasurementData measurementData = measurementService.getMeasurements().get(processId);
                    dashboardWebSocket.publishMeasurementData(measurementData);
                    measurementService.getMeasurements().remove(processId);
                    log.info("Migration action applied successfully for microservice {} suggested at {} by LDM {}!", migrationAction.microservice().getId(), migrationAction.suggestedAt(), migrationAction.suggesterId());
                })
                .exceptionally(ex -> {
                    log.error("Error while sending migration action: ", ex);
                    return null;
                });
    }

    static void triggerLeaderChange(
            LDMStateMachine LDMStateMachine,
            LeaderChangeHandler<RaftGroupMemberId, RaftPeerId, RaftServer, RaftGroupId> leaderChangeHandler,
            String processId,
            MetricsAggregator measurementService,
            String triggerReason,
            DashboardWebSocket dashboardWebSocket
    ) {
        log.debug("Triggering leader change for process: " + processId);
        long endTime = System.nanoTime();
        log.debug("End time of measurement for processId<" + processId + ">: " + endTime);
        log.debug(String.valueOf(System.nanoTime()));
        measurementService.recordEnd(processId, endTime, triggerReason);
        MeasurementData measurementData = measurementService.getMeasurements().get(processId);
        dashboardWebSocket.publishMeasurementData(measurementData);
        measurementService.getMeasurements().remove(processId);
        LDMStateMachine.getServer()
                .thenAccept(raftServer -> {
                    leaderChangeHandler.triggerLeaderChange(
                            raftServer,
                            raftServer.getId(),
                            LDMStateMachine.getGroupId()
                    ).subscribe().with(
                            unused -> log.info("Leader change triggered successfully."),
                            failure -> log.error("Failed to trigger leader change: {}", failure.getMessage())
                    );
                }).exceptionally(failure -> {
                    log.error("Failed to retrieve raft server: {}", failure.getMessage());
                    return null;
                });
    }
}
