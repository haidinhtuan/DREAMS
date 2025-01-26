package com.ldm.domain.service.impl;

import com.ldm.application.service.ClusterLatencyCache;
import com.ldm.domain.model.K8sCluster;
import com.ldm.domain.model.Microservice;
import com.ldm.domain.service.LatencyPenaltyCalculationService;
import com.ldm.domain.service.QoSCalculationService;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class DefaultQoSCalculationService implements QoSCalculationService  {
    private final ClusterLatencyCache clusterLatencyCache;
    private final LatencyPenaltyCalculationService latencyPenaltyCalculationService;

    public Map.Entry<K8sCluster, Double> calculatePotentialQoSImprovement(Microservice microservice) {
        // Log start of the method and the microservice being analyzed
        log.debug("Calculating potential QoS improvement for Microservice: {}", microservice.getId());

        Map<K8sCluster, Double> totalAffinityPerCluster = microservice.getTotalAffinityPerCluster();

        K8sCluster highestAffinityCluster = null;
        double highestAffinityScore = Double.MIN_VALUE;

        K8sCluster localCluster = null;
        double localAffinityScore = 0;

        // Iterate over the map once to find both the highest affinity cluster and the specified cluster
        for (Map.Entry<K8sCluster, Double> entry : totalAffinityPerCluster.entrySet()) {
            K8sCluster affinityCluster = entry.getKey();
            double currentAffinityValue = entry.getValue();

            log.debug("Checking affinity for Cluster: {} with affinity score: {}", affinityCluster.getId(), currentAffinityValue);

            // Check for the highest affinity cluster
            if (currentAffinityValue > highestAffinityScore) {
                highestAffinityCluster = affinityCluster;
                highestAffinityScore = currentAffinityValue;
                log.debug("New highest affinity cluster: {} with score: {}", highestAffinityCluster.getId(), highestAffinityScore);
            }

            // Check if this is the local cluster
            if (affinityCluster.getId().equalsIgnoreCase(microservice.getK8sCluster().getId())) {
                localCluster = affinityCluster;
                localAffinityScore = currentAffinityValue;
                log.debug("Local cluster identified: {} with affinity score: {}", localCluster.getId(), localAffinityScore);
            }
        }

        if (highestAffinityCluster == null) {
            log.warn("No highest affinity cluster found for Microservice: {}", microservice.getId());
            return Map.entry(microservice.getK8sCluster(), 0.0); // No cluster found
        }

        log.debug("Highest affinity cluster: {} with score: {}", highestAffinityCluster.getId(), highestAffinityScore);
        if(localCluster==null) {
            log.debug("The microservice {} does not have any affinity to its own cluster, which should be considered as a potential migration candidate!", microservice.getId());
        } else {
            log.debug("Local cluster: {} with score: {}", localCluster.getId(), localAffinityScore);
        }

        // If the highest affinity score is not greater than the local affinity, no improvement
        if (highestAffinityScore <= localAffinityScore) {
            log.debug("No QoS improvement possible for Microservice: {}. Highest affinity score: {} is not greater than local affinity score: {}",
                    microservice.getId(), highestAffinityScore, localAffinityScore);
            return Map.entry(microservice.getK8sCluster(), 0.0);
        }

        // Retrieve latency to the highest affinity cluster
        long latencyToHighestAffinityCluster = this.clusterLatencyCache.getLatencyToLDMById(highestAffinityCluster.getId());
        log.debug(">> Retrieved Value of latencyToHighestAffinityCluster: " + latencyToHighestAffinityCluster);

        if(latencyToHighestAffinityCluster==0) log.warn("VALUE of latencyToHighestAffinityCluster might be incorrect since the latency can't be 0!!");

        log.debug("Latency to highest affinity cluster {} is {} ms", highestAffinityCluster.getId(), latencyToHighestAffinityCluster);

        // Calculate latency penalty
        double latencyPenalty = latencyPenaltyCalculationService.getLatencyPenalty(localAffinityScore, highestAffinityScore, latencyToHighestAffinityCluster);
        log.debug("Calculated latency penalty: {} for latency: {} ms, local affinity: {}, highest affinity: {}",
                latencyPenalty, latencyToHighestAffinityCluster, localAffinityScore, highestAffinityScore);

        // Total QoS improvement calculation
        double qosImprovement = (highestAffinityScore - localAffinityScore) - latencyPenalty;
        log.debug("Calculated QoS improvement for Microservice: {} is: {}", microservice.getId(), qosImprovement);

        return Map.entry(highestAffinityCluster, qosImprovement);
    }
}
