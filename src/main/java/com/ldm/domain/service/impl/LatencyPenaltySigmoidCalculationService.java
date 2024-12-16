package com.ldm.domain.service.impl;

import com.ldm.domain.service.LatencyPenaltyCalculationService;
import com.ldm.infrastructure.config.LdmConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

/**
 * Service implementation that calculates the latency penalty using a sigmoid function.
 * The latency penalty is adjusted dynamically based on the affinity difference between
 * the local and target clusters and the latency to the target cluster. This calculation
 * favors migration when the affinity to the target cluster is significantly higher, even if
 * the target is far away.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class LatencyPenaltySigmoidCalculationService implements LatencyPenaltyCalculationService {

    private final LdmConfig ldmConfig;
    /**
     * Calculates the latency penalty for migrating a microservice between clusters based on the
     * affinity difference and the latency to the target cluster. The sigmoid function dynamically
     * adjusts the penalty based on the affinity scores.
     *
     * <p>The penalty is significantly reduced when the target affinity score is much higher than
     * the local affinity score, encouraging migration. However, if the affinity difference is small,
     * the penalty remains higher, discouraging migration purely based on distance/latency.</p>
     *
     * @param localAffinityScore      The affinity score between the microservice and its current local cluster.
     * @param targetAffinityScore     The affinity score between the microservice and the target cluster.
     * @param latencyToTargetCluster  The network latency (in milliseconds) to the target cluster.
     * @return The adjusted latency penalty based on the affinity difference and latency.
     */
    @Override
    public double getLatencyPenalty(double localAffinityScore, double targetAffinityScore, long latencyToTargetCluster) {
        double latencyPenalty;

        // Calculate the distance penalty using a sigmoid function to reduce penalty for high affinity differences
        if (targetAffinityScore > localAffinityScore) {
            // Affinity-driven distance penalty reduction using a sigmoid function
            double affinityDifference = targetAffinityScore - localAffinityScore;
            latencyPenalty = latencyToTargetCluster / (1 + Math.exp(affinityDifference / ldmConfig.proposal().scalingFactor()));
        } else {
            latencyPenalty = latencyToTargetCluster; // Regular penalty if target affinity isn't significantly higher
        }
        return latencyPenalty;
    }
}
