package com.dreams.modules;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Inter-Domain Communication Module (ICM) — Inter-domain Migration Coordinator,
 * LDM Discovery Service, and Inter-domain Communication Service.
 * Manages communication between LDM instances across domains.
 *
 * The communication logic is implemented in:
 * - {@link com.dreams.infrastructure.adapter.in.pekko.LdmDiscoveryService} (Pekko cluster membership)
 * - {@link com.dreams.infrastructure.adapter.in.pekko.HealthExchangeService} (health checks + latency measurement)
 * - {@link com.dreams.infrastructure.adapter.out.pekko.PingManager} (ping orchestration)
 */
@ApplicationScoped
@Slf4j
public class InterDomainCommunicationModule {
    // Inter-domain communication is handled by Apache Pekko clustering.
    // Peer discovery uses Pekko receptionist with gossip-based protocol.
    // Latency measurement uses HealthExchangeService with configurable intervals.
}
