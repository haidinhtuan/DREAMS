package com.ldm.application.service;

import com.ldm.domain.model.Microservice;
import com.ldm.domain.model.MigrationAction;
import com.ldm.domain.model.MigrationCandidate;
import com.ldm.domain.service.QoSOptimizationService;
import com.ldm.infrastructure.config.LdmConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class DomainManager {
    private final LdmConfig ldmConfig;

    private final QoSOptimizationService qosOptimizationService;

    private final MicroservicesCache microservicesCache;


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
            log.info("[[[[[[ DOMAIN MANAGER QoS Improvement Evaluation Result ]]]]]]]: Leader LDM with ID <" + ldmConfig.id() + "> proposes migration to target cluster: {}", bestMigrationCandidate);
            return bestMigrationCandidate;
        }
        log.info("[[[[[[ DOMAIN MANAGER QoS Improvement Evaluation Result ]]]]]]]: Leader LDM with ID <" + ldmConfig.id() + "> does NOT propose migration since it was BELOW the proposal threshold. <<");
        return null;

    }

    public boolean voteOnMigrationProposal(Microservice migratingMicroservice, String targetLdmId) {
        log.info("Evaluating migration proposal {},  target LDM: {}", migratingMicroservice, targetLdmId);
        return this.qosOptimizationService.shouldApproveMigrationProposal(migratingMicroservice, targetLdmId);
    }


    public void updateState(MigrationAction migrationAction) {
        if (migrationAction.targetK8sCluster().getId().equalsIgnoreCase(this.ldmConfig.id())) {
            this.microservicesCache.cacheMicroservice(migrationAction.microservice().getId(), migrationAction.microservice());
            log.debug("Applied migration action and added the corresponding microservice to the local state: {}", migrationAction);
        } else if (migrationAction.microservice().getK8sCluster().getId().equalsIgnoreCase(ldmConfig.id())
                && !migrationAction.targetK8sCluster().getId().equalsIgnoreCase(ldmConfig.id())) {
            this.microservicesCache.removeMicroserviceById(migrationAction.microservice().getId());
            log.debug("Removed microservice from the local state since it was moved to another cluster after migration: {}", migrationAction);
        } else {
            log.debug("Migration action did not affect the local state.");
        }

        Microservice migratedMicroservice = new Microservice(migrationAction.microservice().getId(), migrationAction.microservice().getName()
                , migrationAction.microservice().isNonMigratable(), migrationAction.targetK8sCluster(), migrationAction.microservice().getAffinities(),
                migrationAction.microservice().getDataExchangedWithServices(), migrationAction.microservice().getCpuUsage(), migrationAction.microservice().getMemoryUsage());


        microservicesCache.updateMicroserviceIfExists(migratedMicroservice.getId(), migratedMicroservice)
                .subscribe().with(
                        unused -> log.debug("Cached microservice "),
                        failure -> log.error("An error occurred during the update: " + failure.getMessage())
                );

        updateAffinityMicroservices(migratedMicroservice);

        log.info("Applied migractionAction: Moved microservice {} from cluster {} to cluster {}", migrationAction.microservice().getId(), migrationAction.microservice().getK8sCluster().getId(), migrationAction.targetK8sCluster().getId());


        log.info("Microservices Cache AFTER migration action executed: {}", microservicesCache);
        microservicesCache.outputCache();
    }


    public void updateAffinityMicroservices(Microservice migratedMicroservice) {
        this.microservicesCache.getAllMicroservices().forEach(microservice -> {
            Double affinity = microservice.getAffinities().get(migratedMicroservice);
            if (affinity != null) {
                // Update microservice on affinity map with correct key-value pair
                microservice.getAffinities().put(migratedMicroservice, affinity);
                this.microservicesCache.cacheMicroservice(microservice.getId(), microservice);
            }
        });
    }
}
