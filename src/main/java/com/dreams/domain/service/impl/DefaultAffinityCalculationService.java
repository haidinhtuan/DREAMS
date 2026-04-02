package com.dreams.domain.service.impl;

import com.dreams.domain.model.Microservice;
import com.dreams.domain.service.AffinityCalculationService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DefaultAffinityCalculationService implements AffinityCalculationService {
    @Override
    public double calculateDataAffinity(Microservice u, Microservice v) {
        // Example formula from SAGA
        return u.getDataExchangedWith(v) / u.getTotalDataExchanged();
    }

    @Override
    public double calculateTotalAffinity(Microservice u, Microservice v, Map<String, Double> weights) {
        double dataAffinity = calculateDataAffinity(u, v);

        // Return the weighted sum of all affinity types
        return weights.get("data") * dataAffinity;
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
