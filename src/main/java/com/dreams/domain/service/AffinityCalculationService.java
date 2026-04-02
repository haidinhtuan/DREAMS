package com.dreams.domain.service;

import com.dreams.domain.model.Microservice;

import java.util.List;
import java.util.Map;

public interface AffinityCalculationService {
    double calculateDataAffinity(Microservice u, Microservice v);

    double calculateCouplingAffinity(Microservice u, Microservice v);

    double calculateFunctionalAffinity(Microservice u, Microservice v);

    double calculateOperationalAffinity(Microservice u, Microservice v);

    double calculatePrivacyAffinity(Microservice u, Microservice v);

    double calculateTotalAffinity(Microservice u, Microservice v, Map<String, Double> weights);

    double calculateTotalAffinityImpact(Microservice migratingMicroservice, List<Microservice> microservices);
}
