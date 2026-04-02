package com.dreams.domain.service;

import com.dreams.domain.model.K8sCluster;
import com.dreams.domain.model.Microservice;

import java.util.Map;

public interface QoSCalculationService {
    Map.Entry<K8sCluster, Double> calculatePotentialQoSImprovement(Microservice migrationCandidate);
}
