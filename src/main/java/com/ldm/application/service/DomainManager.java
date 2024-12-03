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
        MigrationCandidate bestMigrationCandidate = this.qosOptimizationService.findBestMigrationCandidate();

        if(bestMigrationCandidate==null) {
            log.info("Leader LDM with ID <" + ldmConfig.id() + "> does not propose migration since no candidate is suitable!");
            return null;
        }
        // Only propose migration if improvement is significant
        if (bestMigrationCandidate.improvementScore() > this.ldmConfig.proposal().threshold()) {
            log.info("Leader LDM with ID <" + ldmConfig.id() + "> proposes migration to target cluster.");
            return bestMigrationCandidate;
        }
        log.info("Leader LDM with ID <" + ldmConfig.id() + "> does not propose migration since it was below the proposal threshold.");
        return null;

    }

    public boolean voteOnMigrationProposal(Microservice migratingMicroservice, String targetLdmId) {
        log.info("Evaluating migration proposal {},  target LDM: {}", migratingMicroservice, targetLdmId);
        return this.qosOptimizationService.shouldApproveMigrationProposal(migratingMicroservice, targetLdmId);
    }

    public void updateState(MigrationAction migrationAction) {
        this.microservicesCache.cacheMicroservice(migrationAction.microservice().getId(), migrationAction.microservice());

        this.microservicesCache.getMicroserviceById(migrationAction.microservice().getId())
                .subscribe().with(
                        microservice -> log.info("The following microservice has been updated in the cache: {}", microservice),
                        failure -> log.error("Failed to retrieve microservice with ID: {}", migrationAction.microservice().getId(), failure)
                );

//        Uni<Microservice> microserviceById = this.microservicesCache.getMicroserviceById(migrationAction.microservice().getId());
//        log.info("The following microservice has been updated in the cache: {}", this.microservicesCache.getMicroserviceById(migrationAction.microservice().getId()));
    }


}
