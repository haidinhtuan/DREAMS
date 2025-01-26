package com.ldm.infrastructure.adapter.in.websocket;

import com.ldm.application.service.LdmStateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
                String graphData = ldmStateService.getGraphData().build().toString();
                log.debug("----------->>>>>>> Sending graph data to client...");
                log.debug(graphData);
                session.getBasicRemote().sendText(graphData);
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
                String graphData = ldmStateService.getGraphData().build().toString();
                log.debug("----------->>>>>>> BROADCASTING graph data to the clients...");
                log.debug(graphData);
                broadcast(graphData);
            } catch (Exception e) {
                log.error("Broadcasting graph data to all websocket clients failed!", e);
            }
        });
    }

    public void broadcast(String message) {
        log.debug("----------->>>>>>> BROADCASTING graph data to the clients...");
        sessions.values().forEach(s -> {
            s.getAsyncRemote().sendObject(message, result ->  {
                if (result.getException() != null) {
                    log.error("Unable to send message: " + result.getException());
                }
            });
        });
    }
}
