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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/dashboard")
@ApplicationScoped
@Slf4j
public class DashboardWebSocket {
    Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Inject
    LdmStateService ldmStateService;

    @OnOpen
    public void onOpen(Session session) {
        sessions.put(session.getId(), session);
        try {
            session.getBasicRemote().sendText(ldmStateService.getGraphData().build().toString());
        } catch (IOException e) {
            log.error("Error occurred during the graph data retrieval!", e);
        }
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

    public void broadcast(String message) {
        sessions.values().forEach(s -> {
            s.getAsyncRemote().sendObject(message, result ->  {
                if (result.getException() != null) {
                    log.error("Unable to send message: " + result.getException());
                }
            });
        });
    }
}
