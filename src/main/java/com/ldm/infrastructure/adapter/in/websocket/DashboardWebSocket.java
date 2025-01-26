package com.ldm.infrastructure.adapter.in.websocket;

import com.ldm.application.service.LdmStateService;
import com.ldm.shared.constants.MessageType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
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

    @OnOpen
    public void onOpen(Session session) {
        sessions.put(session.getId(), session);

        managedExecutor.execute(() -> {
            try {
                JsonObject jsonObject = ldmStateService.getGraphData().build();
                log.debug("----------->>>>>>> Sending graph data to client...");
                JsonObject jsonResponse = Json.createObjectBuilder()
                        .add("type", MessageType.GRAPH_DATA.toString())
                        .add("value", jsonObject)
                        .build();
                log.debug(jsonResponse.toString());
                session.getBasicRemote().sendText(jsonResponse.toString());
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
        throwable.printStackTrace();
    }

    public void broadcastLdmState(){
        managedExecutor.execute(() -> {
            try {
                JsonObject jsonObject = ldmStateService.getGraphData().build();
                log.debug("----------->>>>>>> BROADCASTING graph data to the clients...");
                broadcast(MessageType.GRAPH_DATA, jsonObject);
            } catch (Exception e) {
                log.error("Broadcasting graph data to all websocket clients failed!", e);
            }
        });
    }

    public void broadcast(MessageType messageType, JsonObject message) {
        log.debug("----------->>>>>>> BROADCASTING data to the clients...");

        JsonObject jsonResponse = Json.createObjectBuilder()
                .add("type", messageType.toString())
                .add("value", message)
                .build();

        log.debug(jsonResponse.toString());

        sessions.values().forEach(s -> {
            s.getAsyncRemote().sendObject(jsonResponse.toString(), result ->  {
                if (result.getException() != null) {
                    log.error("Unable to send message: " + result.getException());
                }
            });
        });
    }
}
