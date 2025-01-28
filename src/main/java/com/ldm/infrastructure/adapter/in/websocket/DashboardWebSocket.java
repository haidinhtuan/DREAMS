package com.ldm.infrastructure.adapter.in.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldm.application.service.LdmStateService;
import com.ldm.domain.measurement.MeasurementData;
import com.ldm.domain.measurement.MeasurementDataDTO;
import com.ldm.infrastructure.adapter.in.projection.ClusterStateProjectionR2dbcHandler;
import com.ldm.infrastructure.adapter.in.ratis.LDMStateMachine;
import com.ldm.shared.constants.KeyFigureEnum;
import com.ldm.shared.constants.MessageTypeEnum;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/dashboard")
@ApplicationScoped
@Slf4j
public class DashboardWebSocket {
    Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Inject
    LdmStateService ldmStateService;

    @Inject
    ManagedExecutor managedExecutor;

    @Inject
    ObjectMapper objectMapper;

    @OnOpen
    public void onOpen(Session session) {
        sessions.put(session.getId(), session);

        managedExecutor.execute(() -> {
            try {
                JsonObjectBuilder leaderChangedJsonObjectBuilder = Json.createObjectBuilder();
                leaderChangedJsonObjectBuilder.add("NEW_LEADER", LDMStateMachine.currentLeader);
                leaderChangedJsonObjectBuilder.add(KeyFigureEnum.LEADER_CHANGE_COUNT.toString(), LDMStateMachine.leaderChangeCount);
                JsonObject leaderChangedJsonResponse = Json.createObjectBuilder()
                        .add("type", MessageTypeEnum.LEADER_CHANGED.toString())
                        .add("value", leaderChangedJsonObjectBuilder.build())
                        .build();

                JsonObjectBuilder migrationsAppliedJsonObjectBuilder = Json.createObjectBuilder();
                migrationsAppliedJsonObjectBuilder.add("lastMigratedMicroservice", ClusterStateProjectionR2dbcHandler.lastMigratedMicroservice);
                migrationsAppliedJsonObjectBuilder.add("migrationsAppliedCount", ClusterStateProjectionR2dbcHandler.migrationsAppliedCount);

                JsonObject migrationAppliedJsonResponse = Json.createObjectBuilder()
                        .add("type", MessageTypeEnum.MIGRATION_APPLIED.toString())
                        .add("value", migrationsAppliedJsonObjectBuilder.build())
                        .build();

                JsonObject jsonObject = ldmStateService.getGraphData().build();
                log.debug("----------->>>>>>> Sending initial data to client...");
                JsonObject globalStateResponse = Json.createObjectBuilder()
                        .add("type", MessageTypeEnum.GRAPH_DATA.toString())
                        .add("value", jsonObject)
                        .build();
                log.debug(leaderChangedJsonResponse.toString());
                log.debug(migrationAppliedJsonResponse.toString());
                log.debug(globalStateResponse.toString());
                session.getBasicRemote().sendText(leaderChangedJsonResponse.toString());
                session.getBasicRemote().sendText(migrationAppliedJsonResponse.toString());
                session.getBasicRemote().sendText(globalStateResponse.toString());
            } catch (Exception e) {
                log.error("Sending graph data to the new websocket client failed!", e);
            }
        });
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        sessions.remove(session.getId());
        log.error("Error occurred on websocket: ", throwable);
    }

    public void broadcastLdmState() {
        managedExecutor.execute(() -> {
            try {
                JsonObject jsonObject = ldmStateService.getGraphData().build();
                log.debug("----------->>>>>>> BROADCASTING graph data to the clients...");
                broadcast(MessageTypeEnum.GRAPH_DATA, jsonObject);
            } catch (Exception e) {
                log.error("Broadcasting graph data to all websocket clients failed!", e);
            }
        });
    }

    public void broadcast(MessageTypeEnum messageTypeEnum, JsonObject message) {
        log.debug("----------->>>>>>> BROADCASTING data to the clients...");

        JsonObject jsonResponse = Json.createObjectBuilder()
                .add("type", messageTypeEnum.toString())
                .add("value", message)
                .build();

        log.debug(jsonResponse.toString());

        sessions.values().forEach(s -> {
            s.getAsyncRemote().sendObject(jsonResponse.toString(), result -> {
                if (result.getException() != null) {
                    log.error("Unable to send message: " + result.getException());
                }
            });
        });
    }

    public void publishMeasurementData(MeasurementData data) {
        log.debug("----------->>>>>>> BROADCASTING measurement data to the clients...");
        try {
            MeasurementDataDTO dto = new MeasurementDataDTO(data);

            String measurementJson = objectMapper.writeValueAsString(dto);
            log.debug(measurementJson);
            sessions.values().forEach(s -> {
                s.getAsyncRemote().sendObject(measurementJson, result -> {
                    if (result.getException() != null) {
                        log.error("Unable to send measurement data: " + result.getException());
                    }
                });
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
