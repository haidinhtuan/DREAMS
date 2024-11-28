package com.ldm.domain.service;

import com.ldm.domain.model.K8sCluster;
import com.ldm.domain.model.Microservice;

import java.util.Map;

public interface QoSCalculationService {
    Map.Entry<K8sCluster, Double> calculatePotentialQoSImprovement(Microservice migrationCandidate);
}
