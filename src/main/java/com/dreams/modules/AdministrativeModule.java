package com.dreams.modules;

import com.dreams.infrastructure.adapter.in.websocket.DashboardWebSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Administrative Module (AM) — Dashboard, policy management, and visualization.
 * Provides the external-facing interface for monitoring and managing the LDM.
 */
@ApplicationScoped
@Slf4j
public class AdministrativeModule {

    @Inject
    DashboardWebSocket dashboardWebSocket;

    public void broadcastStateUpdate() {
        dashboardWebSocket.broadcastLdmState();
    }
}
