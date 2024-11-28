package com.ldm.domain.service.impl;

import com.ldm.application.service.ClusterLatencyCache;
import com.ldm.application.service.MicroservicesCache;
import com.ldm.domain.service.AffinityCalculationService;
import com.ldm.domain.service.QoSCalculationService;
import com.ldm.domain.service.QoSOptimizationService;
import com.ldm.domain.model.K8sCluster;
import com.ldm.domain.model.Microservice;
import com.ldm.domain.model.MigrationCandidate;
import com.ldm.domain.service.AffinityPenaltyWeightCalculationService;
import com.ldm.infrastructure.config.LdmConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
public class DefaultOptimizationService implements QoSOptimizationService {

    @ConfigProperty(name = "ldm.voting.max-affinity-value", defaultValue = "100")
    int maxAffinityValue;

    @ConfigProperty(name = "ldm.voting.max-latency-value", defaultValue = "1000")
    int maxLatencyValue;

    private final LdmConfig ldmConfig;

    private final MicroservicesCache microservicesCache;
    private final ClusterLatencyCache clusterLatencyCache;

    private final QoSCalculationService qosCalculationService;

    // Voting
    private final AffinityCalculationService affinityCalculationService;
    private final AffinityPenaltyWeightCalculationService affinityPenaltyWeightCalculationService;

    /**
     * Finds the best migration candidate among the microservices in the current cluster.
     * <p>
     * The method iterates through all microservices in the current cluster, evaluates their potential
     * Quality of Service (QoS) improvement if migrated, and returns the microservice that has the highest
     * potential QoS improvement.
     * </p>
     * <p>
     * Non-migratable microservices are filtered out before evaluation. The method also skips any
     * microservice that would result in no QoS improvement (QoS improvement score of 0.0). The
     * microservice with the highest potential QoS improvement is returned along with the target cluster
     * where the improvement can be achieved.
     * </p>
     *
     * @return {@link MigrationCandidate} containing the best candidate microservice, the highest potential
     * QoS improvement score, and the target {@link K8sCluster} for migration.
     * Returns {@code null} if no suitable migration candidate is found or if there are no migratable
     * microservices in the current cluster.
     */
    public MigrationCandidate findBestMigrationCandidate() {
        // Log the start of the method
        log.info("Finding best migration candidate among microservices in the current cluster.");

        Microservice bestCandidate = null;
        K8sCluster bestK8sClusterForCandidate = null;
        double highestQoSImprovement = Double.MIN_VALUE;

        // Get all migratable microservices from the cache
        List<Microservice> microservices = this.microservicesCache.getAllMicroservices()
                .stream()
                .filter(microservice -> !microservice.isNonMigratable()).toList();

        if (microservices.isEmpty()) {
            log.warn("No migratable microservices found in the current cluster.");
            return null;
        }

        // Iterate over all eligible microservices
        for (Microservice microservice : microservices) {
            log.debug("Evaluating Microservice: {}", microservice.getId());

            // Calculate potential QoS improvement
            Map.Entry<K8sCluster, Double> potentialQoSImprovementMap = this.qosCalculationService
                    .calculatePotentialQoSImprovement(microservice);

            double potentialQoSImprovement = potentialQoSImprovementMap.getValue();

            // Skip microservices with no QoS improvement
            if (potentialQoSImprovement == 0.0) {
                log.debug("Skipping Microservice {} due to no QoS improvement", microservice.getId());
                continue;
            }

            // Track the microservice with the highest QoS improvement
            if (potentialQoSImprovement > highestQoSImprovement) {
                log.info("New best candidate found: Microservice {} with QoS improvement: {}",
                        microservice.getId(), potentialQoSImprovement);
                highestQoSImprovement = potentialQoSImprovement;
                bestCandidate = microservice;
                bestK8sClusterForCandidate = potentialQoSImprovementMap.getKey();
            }
        }

        // Log if no candidate was found
        if (bestCandidate == null) {
            log.warn("No suitable migration candidate found in the current cluster.");
            return null;
        }

        // Log the best candidate that is being returned
        log.info("Best migration candidate: Microservice {} with QoS improvement: {}",
                bestCandidate.getId(), highestQoSImprovement);

        // Return the best candidate with the highest QoS improvement
        return new MigrationCandidate(bestCandidate, highestQoSImprovement, bestK8sClusterForCandidate, LocalDateTime.now(), this.ldmConfig.id());
    }


    /**
     * Evaluates a migration proposal for the migrating microservice based on the local microservices' affinity
     * to the migrating microservice and the latency differences between the source and target clusters. The voting
     * decision is made by applying a sigmoid-based affinity penalty weight to the latency penalty, ensuring a more
     * controlled scaling of penalties for high-affinity migrations. The decision also accounts for an impact threshold
     * to allow minimal negative impacts if within acceptable limits.
     *
     * <p>Key considerations in the voting process:</p>
     * <ul>
     *     <li>High affinity combined with large latency differences results in a higher penalty, likely leading to a negative vote.</li>
     *     <li>Low affinity and minor latency differences result in a lower penalty, favoring a positive vote.</li>
     *     <li>Normalized affinity impact ensures that the model is adaptive to various scales.</li>
     *     <li>A threshold is applied to allow small negative impacts in the interest of global QoS improvements.</li>
     * </ul>
     *
     * @param migratingMicroservice The microservice proposed for migration.
     * @param targetLdmId           The ID of the target cluster for migration.
     * @return {@code true} if the LDM votes in favor of the migration, {@code false} otherwise.
     */
    public boolean shouldApproveMigrationProposal(Microservice migratingMicroservice, String targetLdmId) {
        List<Microservice> microservices = this.microservicesCache.getAllMicroservices();

        long latencyToSourceCluster = clusterLatencyCache.getLatencyToLDMById(migratingMicroservice.getK8sCluster().getId());
        long latencyToTargetCluster = clusterLatencyCache.getLatencyToLDMById(targetLdmId);

        // Compare the latency to source and target cluster to evaluate migration impact
        // positive value would result in a positive impact for the local microservices since the latency will decrease or stay the same
        // negative value would result in a negative impact for the local microservices since the latency will increase
//        int latencyDifference = latencyToSourceCluster - latencyToTargetCluster;
        double totalAffinityImpact = affinityCalculationService.calculateTotalAffinityImpact(migratingMicroservice, microservices);

        // Special case: If the affinity impact is low, ignore the latency penalty
        if (totalAffinityImpact == 0) {
            log.info("LDM with ID " + ldmConfig.id() + " votes YES for migration (no local impact).");
            return true;
        }

        // Normalize the affinity impact (assuming maxAffinityValue is known)
        double normalizedAffinityImpact = totalAffinityImpact / maxAffinityValue;

        // Calculate latency difference
        double latencyDifference = latencyToTargetCluster - latencyToSourceCluster;

        // Calculate the affinity penalty weight using the sigmoid function
        double affinityPenaltyWeight = affinityPenaltyWeightCalculationService.calculateAffinityPenaltyWeight(normalizedAffinityImpact);

        // Apply the affinity penalty weight to the latency difference
        double scaledLatencyPenalty = (latencyDifference / maxLatencyValue) * affinityPenaltyWeight;

        // Voting logic: Allow small negative impact within a defined threshold
        if (scaledLatencyPenalty < ldmConfig.voting().threshold()) {
            log.info("LDM with ID " + ldmConfig.id() + " votes YES for migration.");
            return true;
        } else {
            log.info("LDM with ID " + ldmConfig.id() + " votes NO for migration.");
            return false;
        }
    }
}
