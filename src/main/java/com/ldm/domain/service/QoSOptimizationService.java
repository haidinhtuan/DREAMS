package com.ldm.domain.service;

import com.ldm.domain.model.Microservice;
import com.ldm.domain.model.MigrationCandidate;

public interface QoSOptimizationService {

    MigrationCandidate findBestMigrationCandidate();

    boolean shouldApproveMigrationProposal(Microservice migratingMicroservice, String targetLdmId);
}
