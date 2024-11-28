package com.ldm.domain.service.impl;

import com.ldm.domain.model.Microservice;
import com.ldm.domain.service.AffinityCalculationService;
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

//    public double calculateCouplingAffinity(Microservice u, Microservice v) {
//        return u.getMessagesExchangedWith(v) / u.getTotalMessagesExchanged();
//    }

    @Override
    public double calculateTotalAffinity(Microservice u, Microservice v, Map<String, Double> weights) {
        double dataAffinity = calculateDataAffinity(u, v);
//        double privacyAffinity = calculatePrivacyAffinity(u, v);
//        double couplingAffinity = calculateCouplingAffinity(u, v);
//        double functionalAffinity = calculateFunctionalAffinity(u, v);
//        double operationalAffinity = calculateOperationalAffinity(u, v);

        // Return the weighted sum of all affinity types
        return weights.get("data") * dataAffinity;
//                weights.get("privacy") * privacyAffinity +
//                weights.get("coupling") * couplingAffinity +
//                weights.get("functional") * functionalAffinity +
//                weights.get("operational") * operationalAffinity;
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
