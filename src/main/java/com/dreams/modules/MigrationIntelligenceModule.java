package com.dreams.modules;

import com.dreams.application.service.MigrationEligibilityEvaluator;
import com.dreams.domain.model.Microservice;
import com.dreams.domain.model.MigrationCandidate;
import com.dreams.domain.service.QoSOptimizationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration Intelligence Module (MIM) — Migration Eligibility Evaluator (leader role)
 * and Cost-Benefit Analyzer (follower role).
 * Determines optimal migration candidates and evaluates migration proposals.
 */
@ApplicationScoped
@Slf4j
public class MigrationIntelligenceModule {

    @Inject
    MigrationEligibilityEvaluator domainManager;

    @Inject
    QoSOptimizationService optimizationService;

    /**
     * Leader role: find the best migration candidate in the local domain.
     */
    public MigrationCandidate findMigrationCandidate() {
        return domainManager.findMigrationCandidate();
    }

    /**
     * Follower role: evaluate whether to approve a migration proposal.
     */
    public boolean evaluateProposal(Microservice migratingMicroservice, String targetLdmId) {
        return optimizationService.shouldApproveMigrationProposal(migratingMicroservice, targetLdmId);
    }
}
