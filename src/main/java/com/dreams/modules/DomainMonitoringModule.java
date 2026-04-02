package com.dreams.modules;

import com.dreams.application.service.InterDomainLatencyMonitor;
import com.dreams.application.service.ServiceHealthMonitor;
import com.dreams.domain.model.Microservice;
import com.dreams.domain.service.AffinityCalculationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Domain Monitoring Module (DMM) — Service Health Monitor and Service Affinity Calculator.
 * Monitors microservice health within the local domain and computes affinity metrics.
 */
@ApplicationScoped
@Slf4j
public class DomainMonitoringModule {

    @Inject
    ServiceHealthMonitor microservicesCache;

    @Inject
    InterDomainLatencyMonitor clusterLatencyCache;

    @Inject
    AffinityCalculationService affinityCalculationService;

    public List<Microservice> getAllMicroservices() {
        return microservicesCache.getAllMicroservices();
    }

    public List<Microservice> getMigratableMicroservices() {
        return microservicesCache.getAllMicroservices().stream()
                .filter(ms -> !ms.isNonMigratable())
                .toList();
    }

    public double getAffinityImpact(Microservice migrating, List<Microservice> localServices) {
        return affinityCalculationService.calculateTotalAffinityImpact(migrating, localServices);
    }

    public long getLatencyToCluster(String clusterId) {
        return clusterLatencyCache.getLatencyToLDMById(clusterId);
    }
}
