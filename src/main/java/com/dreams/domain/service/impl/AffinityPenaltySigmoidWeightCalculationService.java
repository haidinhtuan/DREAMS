package com.dreams.domain.service.impl;

import com.dreams.domain.service.AffinityPenaltyWeightCalculationService;
import com.dreams.infrastructure.config.LdmConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation that calculates the weighted affinity penalty using a sigmoid function. This function
 * dynamically adjusts the penalty based on the total affinity impact and a configurable scaling factor.
 * The steeper the function, the higher the penalty applied when the affinity impact is large, ensuring that
 * high local affinity combined with latency results in a higher penalty.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class AffinityPenaltySigmoidWeightCalculationService implements AffinityPenaltyWeightCalculationService {

    private final LdmConfig ldmConfig;

    /**
     * Calculates the weighted affinity penalty using a sigmoid function. This method applies a steeper function to
     * assign higher penalties when affinities are large, particularly when considering high-latency migration scenarios.
     *
     * @param totalAffinityImpact The total affinity impact between the local microservices and the migrating microservice.
     * @return The calculated penalty based on the affinity impact and scaling factor.
     */
    @Override
    public double calculateAffinityPenaltyWeight(double totalAffinityImpact) {
        log.debug("Calculating calculateAffinityPenaltyWeight...");
        log.debug("totalAffinityImpact: -" + totalAffinityImpact);
        log.debug("scalingFactor: -" + ldmConfig.voting().scalingFactor());

        // Use a steeper function to apply higher penalties for large affinities and far distances (high latencies)
        return 1.0 / (1 + Math.exp(-totalAffinityImpact / ldmConfig.voting().scalingFactor()));  // Steeper decay based on affinity impact
    }
}
