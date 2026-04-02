package com.dreams.domain.service;

import com.dreams.domain.model.Microservice;
import com.dreams.domain.model.MigrationCandidate;

public interface QoSOptimizationService {

    MigrationCandidate findBestMigrationCandidate();

    boolean shouldApproveMigrationProposal(Microservice migratingMicroservice, String targetLdmId);
}
