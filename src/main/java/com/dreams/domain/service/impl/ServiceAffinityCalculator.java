package com.dreams.domain.service.impl;

import com.dreams.domain.model.Microservice;
import com.dreams.domain.service.AffinityCalculationService;
import com.dreams.infrastructure.config.LdmConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
public class ServiceAffinityCalculator implements AffinityCalculationService {

    private final LdmConfig ldmConfig;

    /** Penalty applied when microservices are in different privacy/security groups (Eq. 12.3.5). */
    private static final double PRIVACY_VIOLATION_PENALTY = -1.0;

    /**
     * Data Affinity (Eq. 12.3.1): d(u,v) = b(u,v) / B(Δt)
     * Normalized ratio of bytes exchanged between u and v to total bytes exchanged by u.
     */
    @Override
    public double calculateDataAffinity(Microservice u, Microservice v) {
        double total = u.getTotalDataExchanged();
        return total > 0 ? u.getDataExchangedWith(v) / total : 0.0;
    }

    /**
     * Coupling Affinity (Eq. 12.3.2): c(u,v) = m(u,v) / M(Δt)
     * Normalized count of messages exchanged between u and v.
     */
    @Override
    public double calculateCouplingAffinity(Microservice u, Microservice v) {
        double total = u.getTotalMessagesExchanged();
        return total > 0 ? u.getMessagesExchangedWith(v) / total : 0.0;
    }

    /**
     * Functional Affinity (Eq. 12.3.3): Sim_func(u,v) ∈ [0,1]
     * A similarity score derived from the service dependency graph.
     * Returns 1.0 if u depends on v (or vice versa), 0.0 otherwise.
     */
    @Override
    public double calculateFunctionalAffinity(Microservice u, Microservice v) {
        return u.hasFunctionalDependency(v) ? 1.0 : 0.0;
    }

    /**
     * Operational Affinity (Eq. 12.3.4): o(u,v) = (1 - γ_o) * Sim_op(u,v) - γ_o * Cont(u,v)
     * Balances hardware similarity (attractive) with resource contention (repulsive).
     * Simplified: uses shared deployment events as similarity proxy and CPU*CPU as contention.
     */
    @Override
    public double calculateOperationalAffinity(Microservice u, Microservice v) {
        double gammaOp = ldmConfig.affinityWeights().operationalBalanceFactor();
        // Sim_op: shared deployment events as similarity proxy, normalized to [0,1]
        double totalEvents = u.getTotalDeploymentEvents();
        double simOp = totalEvents > 0 ? u.getSharedDeploymentEventsWith(v) / totalEvents : 0.0;
        // Cont: resource contention = product of normalized CPU usages
        double contention = (u.getCpuUsage() / 100.0) * (v.getCpuUsage() / 100.0);
        return (1 - gammaOp) * simOp - gammaOp * contention;
    }

    /**
     * Security & Privacy Affinity (Eq. 12.3.5):
     * p(u,v) = 0 if same privacy group, -P_violation if different group.
     * Acts as a hard constraint against cross-group placements.
     */
    @Override
    public double calculatePrivacyAffinity(Microservice u, Microservice v) {
        return u.getPrivacyLevel() == v.getPrivacyLevel() ? 0.0 : PRIVACY_VIOLATION_PENALTY;
    }

    @Override
    public double calculateTotalAffinity(Microservice u, Microservice v, Map<String, Double> weights) {
        double dataAffinity = calculateDataAffinity(u, v);
        double couplingAffinity = calculateCouplingAffinity(u, v);
        double functionalAffinity = calculateFunctionalAffinity(u, v);
        double operationalAffinity = calculateOperationalAffinity(u, v);
        double privacyAffinity = calculatePrivacyAffinity(u, v);

        return weights.getOrDefault("data", 0.0) * dataAffinity +
               weights.getOrDefault("coupling", 0.0) * couplingAffinity +
               weights.getOrDefault("functional", 0.0) * functionalAffinity +
               weights.getOrDefault("operational", 0.0) * operationalAffinity +
               weights.getOrDefault("privacy", 0.0) * privacyAffinity;
    }


    @Override
    public double calculateTotalAffinityImpact(Microservice migratingMicroservice, List<Microservice> microservices) {
        double totalImpactScore = 0;

        for (Microservice localMicroservice : microservices) {
            if (localMicroservice.getAffinities().containsKey(migratingMicroservice)) {
                double affinity = localMicroservice.getAffinities().get(migratingMicroservice);
                totalImpactScore += affinity;
            }
        }

        return totalImpactScore;
    }
}
