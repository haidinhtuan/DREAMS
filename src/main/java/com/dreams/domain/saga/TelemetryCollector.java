package com.dreams.domain.saga;

import com.dreams.application.service.ServiceHealthMonitor;
import com.dreams.domain.model.Microservice;
import com.dreams.domain.service.AffinityCalculationService;
import com.dreams.infrastructure.config.LdmConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SAGA Telemetry Collector (STC) — continuously collects runtime data from
 * the messaging infrastructure and orchestration layer.
 * Tracks message flows, data exchange volumes, and operational metrics.
 * Computes pairwise Service Affinity values using the 5-dimensional formula (Eq. 12.1).
 */
@ApplicationScoped
@Slf4j
public class TelemetryCollector {

    @Inject
    ServiceHealthMonitor serviceHealthMonitor;

    @Inject
    AffinityCalculationService affinityCalculationService;

    @Inject
    LdmConfig ldmConfig;

    /**
     * Collect current affinity data and build a weighted graph.
     * Computes pairwise affinity using all 5 dimensions with configurable weights.
     */
    public AffinityGraph collectAndBuildGraph() {
        List<Microservice> microservices = serviceHealthMonitor.getAllMicroservices();
        log.info("STC: Collecting telemetry for {} microservices", microservices.size());

        Map<String, Double> weights = new HashMap<>();
        weights.put("data", ldmConfig.affinityWeights().data());
        weights.put("coupling", ldmConfig.affinityWeights().coupling());
        weights.put("functional", ldmConfig.affinityWeights().functional());
        weights.put("operational", ldmConfig.affinityWeights().operational());
        weights.put("privacy", ldmConfig.affinityWeights().privacy());

        // Compute total affinity for each pair (Eq. 12.1)
        for (Microservice u : microservices) {
            if (u.getAffinities() == null) continue;
            for (Microservice v : u.getAffinities().keySet()) {
                double totalAffinity = affinityCalculationService.calculateTotalAffinity(u, v, weights);
                log.debug("STC: Affinity({}, {}) = {}", u.getId(), v.getId(), totalAffinity);
            }
        }

        AffinityGraph graph = new AffinityGraph(microservices);
        log.info("STC: Built affinity graph with {} vertices, inter-domain affinity = {}",
                graph.size(), graph.calculateInterDomainAffinity());
        return graph;
    }
}
