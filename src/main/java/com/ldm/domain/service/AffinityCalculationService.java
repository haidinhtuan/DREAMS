package com.ldm.domain.service;

import com.ldm.domain.model.Microservice;

import java.util.List;
import java.util.Map;

public interface AffinityCalculationService {
    double calculateDataAffinity(Microservice u, Microservice v);

    double calculateTotalAffinity(Microservice u, Microservice v, Map<String, Double> weights);

    double calculateTotalAffinityImpact(Microservice migratingMicroservice, List<Microservice> microservices);
}
