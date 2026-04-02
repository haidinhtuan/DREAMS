package com.dreams.application.service;

import com.dreams.domain.model.K8sCluster;
import com.dreams.domain.model.Microservice;
import com.dreams.domain.model.MigrationAction;
import com.dreams.domain.model.MigrationCandidate;
import com.dreams.domain.service.QoSOptimizationService;
import com.dreams.infrastructure.config.LdmConfig;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class MigrationEligibilityEvaluator {
    private final LdmConfig ldmConfig;

    private final QoSOptimizationService qosOptimizationService;

    private final ServiceHealthMonitor microservicesCache;


    public MigrationCandidate findMigrationCandidate() {
        log.info("****** Start running QoS Improvement Proposal!******");
        log.debug("Microservices Cache before proposal: {}", microservicesCache);
        microservicesCache.outputCache();
        MigrationCandidate bestMigrationCandidate = this.qosOptimizationService.findBestMigrationCandidate();

        if (bestMigrationCandidate == null) {
            log.info("[[[[[[ DOMAIN MANAGER QoS Improvement Evaluation Result ]]]]]]]:  Leader LDM with ID <" + ldmConfig.id() + "> does not propose migration since no candidate is suitable!");
            return null;
        }
        // Only propose migration if improvement is significant
        if (bestMigrationCandidate.improvementScore() > this.ldmConfig.proposal().threshold()) {
            log.info("*************************** >> MIGRATION CANDIDATE FOUND >> ***************************");
            log.info("*************************** ------------------------- ***************************");
            log.info("[[[[[[ DOMAIN MANAGER QoS Improvement Evaluation Result ]]]]]]]: Leader LDM with ID <" + ldmConfig.id() + "> proposes migration to target cluster: {}", bestMigrationCandidate);
            log.info("*************************** ------------------------- ***************************");
            log.info("*************************** << MIGRATION CANDIDATE FOUND << ***************************");
            return bestMigrationCandidate;
        }
        log.warn("[[[[[[ DOMAIN MANAGER QoS Improvement Evaluation Result ]]]]]]]: Leader LDM with ID <" + ldmConfig.id() + "> does NOT propose migration since it was BELOW the proposal threshold. <<");
        return null;

    }

    /**
     * Find top-K migration candidates that don't conflict with each other.
     * Two candidates conflict if they involve the same microservice or same target cluster.
     */
    public List<MigrationCandidate> findTopMigrationCandidates(int maxCandidates) {
        // Delegate to optimization service for all candidates above threshold
        List<MigrationCandidate> allCandidates = qosOptimizationService.findAllMigrationCandidates();
        if (allCandidates == null || allCandidates.isEmpty()) {
            return Collections.emptyList();
        }

        // Sort by QoS improvement score descending
        allCandidates.sort((a, b) -> Double.compare(b.improvementScore(), a.improvementScore()));

        // Select non-conflicting candidates (greedy)
        List<MigrationCandidate> selected = new ArrayList<>();
        Set<String> usedMicroserviceIds = new HashSet<>();

        for (MigrationCandidate candidate : allCandidates) {
            if (selected.size() >= maxCandidates) break;
            String msId = candidate.microservice().getId();
            if (!usedMicroserviceIds.contains(msId)) {
                selected.add(candidate);
                usedMicroserviceIds.add(msId);
            }
        }

        log.info("Selected {} non-conflicting migration candidates from {} total", selected.size(), allCandidates.size());
        return selected;
    }

    public boolean voteOnMigrationProposal(Microservice migratingMicroservice, String targetLdmId) {
        log.info("Evaluating migration proposal {},  target LDM: {}", migratingMicroservice, targetLdmId);
        return this.qosOptimizationService.shouldApproveMigrationProposal(migratingMicroservice, targetLdmId);
    }


    public Uni<Void> updateState(MigrationAction migrationAction) {
        return Uni.createFrom().item(migrationAction)
                .onItem()
                .transformToUni(migrationActionEvent -> {
                    Microservice migratedMicroservice = new Microservice(migrationAction.microservice().getId(), migrationAction.microservice().getName()
                            , migrationAction.microservice().isNonMigratable(), migrationAction.targetK8sCluster(), migrationAction.microservice().getAffinities(),
                            migrationAction.microservice().getDataExchangedWithServices(),
                            migrationAction.microservice().getMessagesExchangedWithServices(),
                            migrationAction.microservice().getDependsOnServices(),
                            migrationAction.microservice().getSharedDeploymentEvents(),
                            migrationAction.microservice().getPrivacyLevel(),
                            migrationAction.microservice().getCpuUsage(), migrationAction.microservice().getMemoryUsage());
                    if (migrationAction.targetK8sCluster().getId().equalsIgnoreCase(this.ldmConfig.id())) {
                        // New cluster of the candidate
                        log.debug("Applied migration action and added the corresponding microservice to the local state: {}", migrationAction);
                        return this.microservicesCache.addMicroserviceIfNotExistsReactive(migrationActionEvent.microservice().getId(), migratedMicroservice);
                    } else if (migrationAction.microservice().getK8sCluster().getId().equalsIgnoreCase(ldmConfig.id())
                            && !migrationAction.targetK8sCluster().getId().equalsIgnoreCase(ldmConfig.id())) {
                        // Old cluster of the candidate
                        log.debug("Removed microservice from the local state since it was moved to another cluster after migration: {}", migrationAction);
                        return this.microservicesCache.removeMicroserviceByIdReactive(migrationActionEvent.microservice().getId());
                    }
                    // The cluster is not affected, but the cache update is being reevaluated as a safety measure.
                    log.debug("Updating microservice in the cache if it exists.");
                    return microservicesCache.updateMicroserviceIfExists(migratedMicroservice.getId(), migratedMicroservice);
                })
                .onItem()
                .transformToUni(unused -> this.microservicesCache.getAllMicroservicesAsMultiReactive()
                        .onItem()
                        .transformToUniAndMerge(microservice -> {
                            log.debug("--------------------------******MICROSERVICE: "+microservice.getId()+"********-------------------------------");
                            log.debug("{}", microservice);
                            log.debug("--------------------------**************-------------------------------");
                            Double affinity = microservice.getAffinities().get(migrationAction.microservice());
                            if (affinity != null) {
                                Microservice migratingMicroservice = migrationAction.microservice();
                                Optional<Microservice> originalAffinityMicroserviceKeyOptional = microservice.getAffinities().keySet().stream().filter(microservice1 -> microservice1.equals(migratingMicroservice)).findFirst();
                                Microservice originalAffinityMicroserviceKey = originalAffinityMicroserviceKeyOptional.orElse(null);
                                microservice.getAffinities().remove(originalAffinityMicroserviceKey);

                                K8sCluster targetK8sCluster = new K8sCluster(migrationAction.targetK8sCluster().getId(), migrationAction.targetK8sCluster().getLocation());

                                assert originalAffinityMicroserviceKey != null;
                                originalAffinityMicroserviceKey.setK8sCluster(targetK8sCluster);

                                microservice.getAffinities().put(originalAffinityMicroserviceKey, affinity);

                                log.debug("Updating k8sCluster of affinity microservice: {}", microservice);
                                return this.microservicesCache.updateMicroserviceIfExists(
                                        microservice.getId(), microservice);
                            }
                            return Uni.createFrom().voidItem();
                        }).collect().asList()
                        .replaceWithVoid()
                ).replaceWithVoid();
    }

    public void printMicroservicesState(){
        this.microservicesCache.outputCache();
    }
}
